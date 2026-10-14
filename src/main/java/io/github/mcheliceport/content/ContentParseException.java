package io.github.mcheliceport.content;

import java.io.Serial;

/**
 * A content file could not be parsed; carries the 1-based line number of the problem.
 *
 * <p>Ported from upstream {@code com.norwood.mcheli.helper.info.ContentParseException}.
 */
public class ContentParseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 4338814389788695295L;
    private final int lineNo;

    public ContentParseException(int lineNo) {
        this.lineNo = lineNo;
    }

    public ContentParseException(String msg, int lineNo) {
        super(msg);
        this.lineNo = lineNo;
    }

    public ContentParseException(Throwable cause, int lineNo) {
        super(cause);
        this.lineNo = lineNo;
    }

    public int getLineNo() {
        return this.lineNo;
    }
}
