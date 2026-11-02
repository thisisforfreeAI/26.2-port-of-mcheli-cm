package io.github.mcheliceport.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The HUD expression language: a small evaluator with the semantics of the Aviator 5.4.3 subset
 * upstream's HUD files use (decision D4). Upstream compiled every HUD expression with
 * {@code AviatorEvaluator} and read the result in {@code MCH_HudItem#calc}.
 *
 * <p>Values are {@link Long} (whole numbers: integer and hexadecimal literals, and variables of
 * integer types), {@link Double} (decimal literals and variables of floating types),
 * {@link Boolean}, or {@code null} (Aviator's {@code nil}, also the value of unknown variables).
 * As in Aviator:
 * <ul>
 *   <li>{@code long op long} stays a long, so {@code 7/2} is {@code 3}; a double on either side
 *       makes a double; a whole-number division by zero is an error;</li>
 *   <li>{@code << >> >>> & | ^ ~} take longs only; {@code ! && || ?:} take booleans only;</li>
 *   <li>{@code ==} between a boolean and a number is {@code false} (so {@code hovering==1} is never
 *       true), while {@code <} and the like between them is an error; {@code nil} is equal only to
 *       {@code nil} and less than every other value;</li>
 *   <li>precedence is Java's, with {@code **} (power) binding tighter than the unary operators on
 *       its right.</li>
 * </ul>
 * Evaluation errors throw {@link HudExpressionException}; the HUD interpreter reports them.
 */
public final class HudExpression {

    private static final Map<String, HudExpression> CACHE = new ConcurrentHashMap<>();

    private final String source;
    private final Node root;

    private HudExpression(String source, Node root) {
        this.source = source;
        this.root = root;
    }

    /** Parses an expression, or returns the cached parse of the same text. */
    public static HudExpression compile(String source) {
        HudExpression cached = CACHE.get(source);
        if (cached != null) {
            return cached;
        }
        HudExpression parsed = new HudExpression(source, new Parser(source).parseAll());
        CACHE.put(source, parsed);
        return parsed;
    }

    /** Evaluates the expression: a {@link Long}, {@link Double}, {@link Boolean} or {@code null}. */
    public @Nullable Object evaluate(Variables vars) {
        return this.root.eval(vars);
    }

    /**
     * Evaluates as upstream's {@code MCH_HudItem#calc}: numbers as doubles, booleans as 1 or 0; any
     * other result is an error.
     */
    public double calc(Variables vars) {
        Object result = evaluate(vars);
        if (result instanceof Number n) {
            return n.doubleValue();
        }
        if (result instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        throw new HudExpressionException("Expression returned unsupported type: " + result + " in " + this.source);
    }

    /** {@link #compile} and {@link #calc} in one call. */
    public static double calc(String source, Variables vars) {
        return compile(source).calc(vars);
    }

    public String source() {
        return this.source;
    }

    /** Variable values by name; {@code null} for unknown names. */
    @FunctionalInterface
    public interface Variables {
        @Nullable Object get(String name);

        /** Wraps a map; its values are converted as by {@link #normalize}. */
        static Variables of(Map<String, ?> map) {
            return map::get;
        }
    }

    /** An expression that cannot be parsed or evaluated. */
    public static final class HudExpressionException extends RuntimeException {
        public HudExpressionException(String message) {
            super(message);
        }
    }

    /** Converts a Java value to the evaluator's value types, as Aviator wraps variables. */
    static @Nullable Object normalize(@Nullable Object value) {
        if (value == null || value instanceof Long || value instanceof Double || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        if (value instanceof Float f) {
            return f.doubleValue();
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new HudExpressionException("Unsupported variable value " + value);
    }

    // ---------------------------------------------------------------- AST

    private interface Node {
        @Nullable Object eval(Variables vars);
    }

    private record Literal(@Nullable Object value) implements Node {
        @Override
        public @Nullable Object eval(Variables vars) {
            return this.value;
        }
    }

    private record Variable(String name) implements Node {
        @Override
        public @Nullable Object eval(Variables vars) {
            return normalize(vars.get(this.name));
        }
    }

    private record Unary(String op, Node operand) implements Node {
        @Override
        public @Nullable Object eval(Variables vars) {
            Object v = this.operand.eval(vars);
            switch (this.op) {
                case "-":
                    if (v instanceof Long l) {
                        return -l;
                    }
                    if (v instanceof Double d) {
                        return -d;
                    }
                    break;
                case "~":
                    if (v instanceof Long l) {
                        return ~l;
                    }
                    break;
                default:
                    if (v instanceof Boolean b) {
                        return !b;
                    }
                    break;
            }
            throw new HudExpressionException(describe(v) + " doesn't support operation '" + this.op + "'");
        }
    }

    private record Logical(boolean and, Node left, Node right) implements Node {
        @Override
        public Object eval(Variables vars) {
            boolean l = bool(this.left.eval(vars));
            if (this.and ? !l : l) {
                return l;
            }
            return bool(this.right.eval(vars));
        }
    }

    private record Ternary(Node cond, Node then, Node otherwise) implements Node {
        @Override
        public @Nullable Object eval(Variables vars) {
            return bool(this.cond.eval(vars)) ? this.then.eval(vars) : this.otherwise.eval(vars);
        }
    }

    private record Binary(String op, Node left, Node right) implements Node {
        @Override
        public @Nullable Object eval(Variables vars) {
            Object l = this.left.eval(vars);
            Object r = this.right.eval(vars);
            return switch (this.op) {
                case "==" -> equal(l, r);
                case "!=" -> !equal(l, r);
                case "<" -> compare(l, r) < 0;
                case "<=" -> compare(l, r) <= 0;
                case ">" -> compare(l, r) > 0;
                case ">=" -> compare(l, r) >= 0;
                case "+", "-", "*", "/", "%", "**" -> arithmetic(this.op, l, r);
                default -> bitwise(this.op, l, r);
            };
        }
    }

    private record Call(String name, Function<Object[], Object> function, List<Node> args) implements Node {
        @Override
        public Object eval(Variables vars) {
            Object[] values = new Object[this.args.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = this.args.get(i).eval(vars);
            }
            return this.function.apply(values);
        }
    }

    // ---------------------------------------------------------------- semantics

    private static String describe(@Nullable Object v) {
        return v == null ? "nil" : "<" + v.getClass().getSimpleName() + ", " + v + ">";
    }

    private static boolean bool(@Nullable Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        throw new HudExpressionException(describe(v) + " is not a boolean value");
    }

    private static boolean equal(@Nullable Object l, @Nullable Object r) {
        if (l == null || r == null) {
            return l == r;
        }
        if (l instanceof Boolean || r instanceof Boolean) {
            return l.equals(r);
        }
        return compare(l, r) == 0;
    }

    private static int compare(@Nullable Object l, @Nullable Object r) {
        if (l == null || r == null) {
            return l == null ? (r == null ? 0 : -1) : 1;
        }
        if (l instanceof Boolean a && r instanceof Boolean b) {
            return Boolean.compare(a, b);
        }
        if (l instanceof Long a && r instanceof Long b) {
            return Long.compare(a, b);
        }
        if (l instanceof Number a && r instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        throw new HudExpressionException("Could not compare " + describe(l) + " with " + describe(r));
    }

    private static Object arithmetic(String op, @Nullable Object l, @Nullable Object r) {
        if (l instanceof Long a && r instanceof Long b) {
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> {
                    if (b == 0) {
                        throw new HudExpressionException("/ by zero");
                    }
                    yield a / b;
                }
                case "%" -> {
                    if (b == 0) {
                        throw new HudExpressionException("/ by zero");
                    }
                    yield a % b;
                }
                default -> b >= 0 ? powLong(a, b) : Math.pow(a, b);
            };
        }
        if (l instanceof Number a && r instanceof Number b && !(l instanceof Boolean) && !(r instanceof Boolean)) {
            double x = a.doubleValue();
            double y = b.doubleValue();
            return switch (op) {
                case "+" -> x + y;
                case "-" -> x - y;
                case "*" -> x * y;
                case "/" -> x / y;
                case "%" -> x % y;
                default -> Math.pow(x, y);
            };
        }
        throw new HudExpressionException("Could not " + op + " " + describe(l) + " with " + describe(r));
    }

    private static long powLong(long base, long exp) {
        long result = 1;
        for (long i = 0; i < exp; i++) {
            result *= base;
            if (result == 0 || (base == 1)) {
                break;
            }
        }
        return result;
    }

    private static Object bitwise(String op, @Nullable Object l, @Nullable Object r) {
        if (l instanceof Long a && r instanceof Long b) {
            return switch (op) {
                case "<<" -> a << b;
                case ">>" -> a >> b;
                case ">>>" -> a >>> b;
                case "&" -> a & b;
                case "|" -> a | b;
                default -> a ^ b;
            };
        }
        throw new HudExpressionException("Could not " + op + " " + describe(l) + " with " + describe(r));
    }

    private static double number(@Nullable Object v) {
        if (v instanceof Long || v instanceof Double) {
            return ((Number) v).doubleValue();
        }
        throw new HudExpressionException(describe(v) + " is not a number");
    }

    private static Function<Object[], Object> doubleFunction(String name, java.util.function.DoubleUnaryOperator f) {
        return args -> {
            checkArgs(name, args, 1);
            return f.applyAsDouble(number(args[0]));
        };
    }

    private static void checkArgs(String name, Object[] args, int count) {
        if (args.length != count) {
            throw new HudExpressionException("Wrong number of arguments (" + args.length + ") passed to " + name);
        }
    }

    /** The functions of Aviator's standard library that make sense for HUD numbers. */
    private static final Map<String, Function<Object[], Object>> FUNCTIONS = Map.ofEntries(
            Map.entry("math.abs", args -> {
                checkArgs("math.abs", args, 1);
                if (args[0] instanceof Long l) {
                    return Math.abs(l);
                }
                return Math.abs(number(args[0]));
            }),
            Map.entry("math.round", args -> {
                checkArgs("math.round", args, 1);
                return Math.round(number(args[0]));
            }),
            Map.entry("math.floor", doubleFunction("math.floor", Math::floor)),
            Map.entry("math.ceil", doubleFunction("math.ceil", Math::ceil)),
            Map.entry("math.sqrt", doubleFunction("math.sqrt", Math::sqrt)),
            Map.entry("math.log", doubleFunction("math.log", Math::log)),
            Map.entry("math.log10", doubleFunction("math.log10", Math::log10)),
            Map.entry("math.sin", doubleFunction("math.sin", Math::sin)),
            Map.entry("math.cos", doubleFunction("math.cos", Math::cos)),
            Map.entry("math.tan", doubleFunction("math.tan", Math::tan)),
            Map.entry("math.asin", doubleFunction("math.asin", Math::asin)),
            Map.entry("math.acos", doubleFunction("math.acos", Math::acos)),
            Map.entry("math.atan", doubleFunction("math.atan", Math::atan)),
            Map.entry("math.pow", args -> {
                checkArgs("math.pow", args, 2);
                return Math.pow(number(args[0]), number(args[1]));
            }),
            Map.entry("long", args -> {
                checkArgs("long", args, 1);
                return args[0] instanceof Long l ? l : (long) number(args[0]);
            }),
            Map.entry("double", args -> {
                checkArgs("double", args, 1);
                return number(args[0]);
            }),
            Map.entry("max", args -> extreme("max", args, 1)),
            Map.entry("min", args -> extreme("min", args, -1)));

    private static Object extreme(String name, Object[] args, int sign) {
        if (args.length == 0) {
            throw new HudExpressionException("Wrong number of arguments (0) passed to " + name);
        }
        Object best = args[0];
        number(best);
        for (int i = 1; i < args.length; i++) {
            number(args[i]);
            if (compare(args[i], best) * sign > 0) {
                best = args[i];
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- parser

    private static final class Parser {
        private final String src;
        private int pos;

        Parser(String src) {
            this.src = src;
        }

        Node parseAll() {
            Node node = ternary();
            skipSpaces();
            if (this.pos != this.src.length()) {
                throw error("unexpected '" + this.src.charAt(this.pos) + "'");
            }
            return node;
        }

        private HudExpressionException error(String what) {
            return new HudExpressionException("Syntax error in HUD expression '" + this.src + "' at " + this.pos + ": "
                    + what);
        }

        private void skipSpaces() {
            while (this.pos < this.src.length() && Character.isWhitespace(this.src.charAt(this.pos))) {
                this.pos++;
            }
        }

        /** Consumes {@code op} if it is next and not the start of a longer operator in {@code longer}. */
        private boolean accept(String op, String... longer) {
            skipSpaces();
            if (!this.src.startsWith(op, this.pos)) {
                return false;
            }
            for (String l : longer) {
                if (this.src.startsWith(l, this.pos)) {
                    return false;
                }
            }
            this.pos += op.length();
            return true;
        }

        private Node ternary() {
            Node cond = or();
            if (accept("?")) {
                Node then = ternary();
                if (!accept(":")) {
                    throw error("expected ':'");
                }
                Node otherwise = ternary();
                return new Ternary(cond, then, otherwise);
            }
            return cond;
        }

        private Node or() {
            Node left = and();
            while (accept("||")) {
                left = new Logical(false, left, and());
            }
            return left;
        }

        private Node and() {
            Node left = bitOr();
            while (accept("&&")) {
                left = new Logical(true, left, bitOr());
            }
            return left;
        }

        private Node bitOr() {
            Node left = bitXor();
            while (accept("|", "||")) {
                left = new Binary("|", left, bitXor());
            }
            return left;
        }

        private Node bitXor() {
            Node left = bitAnd();
            while (accept("^")) {
                left = new Binary("^", left, bitAnd());
            }
            return left;
        }

        private Node bitAnd() {
            Node left = equality();
            while (accept("&", "&&")) {
                left = new Binary("&", left, equality());
            }
            return left;
        }

        private Node equality() {
            Node left = relational();
            while (true) {
                if (accept("==")) {
                    left = new Binary("==", left, relational());
                } else if (accept("!=")) {
                    left = new Binary("!=", left, relational());
                } else {
                    return left;
                }
            }
        }

        private Node relational() {
            Node left = shift();
            while (true) {
                if (accept("<=")) {
                    left = new Binary("<=", left, shift());
                } else if (accept(">=")) {
                    left = new Binary(">=", left, shift());
                } else if (accept("<", "<<")) {
                    left = new Binary("<", left, shift());
                } else if (accept(">", ">>")) {
                    left = new Binary(">", left, shift());
                } else {
                    return left;
                }
            }
        }

        private Node shift() {
            Node left = additive();
            while (true) {
                if (accept("<<")) {
                    left = new Binary("<<", left, additive());
                } else if (accept(">>>")) {
                    left = new Binary(">>>", left, additive());
                } else if (accept(">>")) {
                    left = new Binary(">>", left, additive());
                } else {
                    return left;
                }
            }
        }

        private Node additive() {
            Node left = multiplicative();
            while (true) {
                if (accept("+")) {
                    left = new Binary("+", left, multiplicative());
                } else if (accept("-")) {
                    left = new Binary("-", left, multiplicative());
                } else {
                    return left;
                }
            }
        }

        private Node multiplicative() {
            Node left = unary();
            while (true) {
                if (accept("*", "**")) {
                    left = new Binary("*", left, unary());
                } else if (accept("/")) {
                    left = new Binary("/", left, unary());
                } else if (accept("%")) {
                    left = new Binary("%", left, unary());
                } else {
                    return left;
                }
            }
        }

        private Node unary() {
            if (accept("!", "!=")) {
                return new Unary("!", unary());
            }
            if (accept("-")) {
                Node operand = unary();
                // Aviator folds a minus in front of a number literal into the literal.
                if (operand instanceof Literal(Long l)) {
                    return new Literal(-l);
                }
                if (operand instanceof Literal(Double d)) {
                    return new Literal(-d);
                }
                return new Unary("-", operand);
            }
            if (accept("~")) {
                return new Unary("~", unary());
            }
            return power();
        }

        private Node power() {
            Node base = primary();
            if (accept("**")) {
                return new Binary("**", base, unary());
            }
            return base;
        }

        private Node primary() {
            skipSpaces();
            if (this.pos >= this.src.length()) {
                throw error("unexpected end");
            }
            char c = this.src.charAt(this.pos);
            if (c == '(') {
                this.pos++;
                Node inner = ternary();
                if (!accept(")")) {
                    throw error("expected ')'");
                }
                return inner;
            }
            if (Character.isDigit(c) || c == '.' && this.pos + 1 < this.src.length()
                    && Character.isDigit(this.src.charAt(this.pos + 1))) {
                return number();
            }
            if (Character.isLetter(c) || c == '_' || c == '$') {
                int start = this.pos;
                while (this.pos < this.src.length()) {
                    char ch = this.src.charAt(this.pos);
                    if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '$' || ch == '.') {
                        this.pos++;
                    } else {
                        break;
                    }
                }
                String name = this.src.substring(start, this.pos);
                switch (name) {
                    case "true":
                        return new Literal(Boolean.TRUE);
                    case "false":
                        return new Literal(Boolean.FALSE);
                    case "nil":
                        return new Literal(null);
                    default:
                        break;
                }
                if (accept("(")) {
                    Function<Object[], Object> function = FUNCTIONS.get(name);
                    if (function == null) {
                        throw error("function not found: " + name);
                    }
                    List<Node> args = new ArrayList<>();
                    if (!accept(")")) {
                        do {
                            args.add(ternary());
                        } while (accept(","));
                        if (!accept(")")) {
                            throw error("expected ')'");
                        }
                    }
                    return new Call(name, function, List.copyOf(args));
                }
                return new Variable(name);
            }
            throw error("unexpected '" + c + "'");
        }

        private Node number() {
            int start = this.pos;
            String s = this.src;
            if (s.startsWith("0x", start) || s.startsWith("0X", start)) {
                this.pos += 2;
                while (this.pos < s.length() && Character.digit(s.charAt(this.pos), 16) >= 0) {
                    this.pos++;
                }
                String hex = s.substring(start + 2, this.pos);
                if (hex.isEmpty()) {
                    throw error("bad hex number");
                }
                return new Literal(Long.parseUnsignedLong(hex, 16));
            }
            boolean decimal = false;
            while (this.pos < s.length() && Character.isDigit(s.charAt(this.pos))) {
                this.pos++;
            }
            if (this.pos < s.length() && s.charAt(this.pos) == '.') {
                decimal = true;
                this.pos++;
                while (this.pos < s.length() && Character.isDigit(s.charAt(this.pos))) {
                    this.pos++;
                }
            }
            if (this.pos < s.length() && (s.charAt(this.pos) == 'e' || s.charAt(this.pos) == 'E')) {
                int save = this.pos;
                this.pos++;
                if (this.pos < s.length() && (s.charAt(this.pos) == '+' || s.charAt(this.pos) == '-')) {
                    this.pos++;
                }
                if (this.pos < s.length() && Character.isDigit(s.charAt(this.pos))) {
                    decimal = true;
                    while (this.pos < s.length() && Character.isDigit(s.charAt(this.pos))) {
                        this.pos++;
                    }
                } else {
                    this.pos = save;
                }
            }
            String text = s.substring(start, this.pos);
            if (this.pos < s.length() && (Character.isLetter(s.charAt(this.pos)) || s.charAt(this.pos) == '_')) {
                throw error("unsupported number suffix in " + text + s.charAt(this.pos));
            }
            if (decimal) {
                return new Literal(Double.parseDouble(text));
            }
            try {
                return new Literal(Long.parseLong(text));
            } catch (NumberFormatException e) {
                // Aviator makes a BigInteger; the HUD only ever reads numbers as doubles.
                return new Literal(Double.parseDouble(text));
            }
        }
    }

    @Override
    public String toString() {
        return "HudExpression[" + this.source + "]";
    }
}
