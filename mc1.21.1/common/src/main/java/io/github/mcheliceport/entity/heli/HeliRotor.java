package io.github.mcheliceport.entity.heli;

import io.github.mcheliceport.util.McheliMath;

/**
 * One rotor and its blades, with the fold and unfold animation: upstream's {@code MCH_Rotor} and
 * {@code MCH_Blade}. Positions are unused until models are drawn (M4).
 */
public final class HeliRotor {

    private final Blade[] blades;
    private final boolean haveFoldBladeFunc;
    private boolean isFoldBlade;
    private boolean isFoldBladeTarget;

    public HeliRotor(int numBlade, int invRot, int foldSpeed, boolean canFoldBlade) {
        int inv = invRot > 0 ? invRot : 1;
        int num = numBlade > 0 ? numBlade : 1;
        this.blades = new Blade[num];
        for (int i = 0; i < num; i++) {
            this.blades[i] = new Blade(i * inv);
            this.blades[i].setFoldRotation(5 + i * 3).setFoldSpeed(foldSpeed);
        }
        this.haveFoldBladeFunc = canFoldBlade;
    }

    public int getBladeNum() {
        return this.blades.length;
    }

    public float getBladeRotation(int bladeIndex) {
        return bladeIndex >= this.blades.length ? 0.0F : this.blades[bladeIndex].rotation;
    }

    public float getPrevBladeRotation(int bladeIndex) {
        return bladeIndex >= this.blades.length ? 0.0F : this.blades[bladeIndex].prevRotation;
    }

    public boolean isFoldingOrUnfolding() {
        return this.isFoldBlade != this.isFoldBladeTarget;
    }

    public boolean isFolded() {
        return this.isFoldBlade && this.isFoldBladeTarget;
    }

    public void startUnfold() {
        this.isFoldBladeTarget = false;
    }

    public void startFold() {
        if (this.haveFoldBladeFunc) {
            this.isFoldBladeTarget = true;
        }
    }

    public void forceFold() {
        if (this.haveFoldBladeFunc) {
            this.isFoldBladeTarget = true;
            this.isFoldBlade = true;
            for (Blade b : this.blades) {
                b.forceFold();
            }
        }
    }

    public void update(float rot) {
        boolean isCmpFoldUnfold = true;
        for (Blade b : this.blades) {
            b.setPrevRotation(b.rotation);
            if (!this.isFoldBlade) {
                if (!this.isFoldBladeTarget) {
                    b.setRotation(rot + b.baseRotation);
                    isCmpFoldUnfold = false;
                } else if (!b.folding()) {
                    isCmpFoldUnfold = false;
                }
            } else if (this.isFoldBladeTarget) {
                isCmpFoldUnfold = false;
            } else if (!b.unfolding(rot)) {
                isCmpFoldUnfold = false;
            }
        }
        if (isCmpFoldUnfold) {
            this.isFoldBlade = this.isFoldBladeTarget;
        }
    }

    /** Upstream {@code MCH_Blade}. */
    static final class Blade {
        private final float baseRotation;
        private float rotation = 0.0F;
        private float prevRotation = 0.0F;
        private float foldSpeed = 3.0F;
        private float foldRotation = 5.0F;

        Blade(float baseRotation) {
            this.baseRotation = baseRotation;
        }

        void setRotation(float rotation) {
            this.rotation = (float) McheliMath.getRotate360(rotation);
        }

        void setPrevRotation(float rotation) {
            this.prevRotation = (float) McheliMath.getRotate360(rotation);
        }

        Blade setFoldSpeed(float speed) {
            if (speed > 0.1) {
                this.foldSpeed = speed;
            }
            return this;
        }

        Blade setFoldRotation(float rotation) {
            if (rotation > this.foldSpeed * 2.0F) {
                this.foldRotation = rotation;
            }
            return this;
        }

        void forceFold() {
            if (this.rotation > this.foldRotation && this.rotation < 360.0F - this.foldRotation) {
                if (this.rotation < 180.0F) {
                    this.rotation = this.foldRotation;
                } else {
                    this.rotation = 360.0F - this.foldRotation;
                }
                this.rotation %= 360.0F;
                this.prevRotation = this.rotation;
            }
        }

        boolean folding() {
            boolean isCmpFolding = false;
            if (this.rotation > this.foldRotation && this.rotation < 360.0F - this.foldRotation) {
                if (this.rotation < 180.0F) {
                    this.rotation = this.rotation - this.foldSpeed;
                } else {
                    this.rotation = this.rotation + this.foldSpeed;
                }
                this.rotation %= 360.0F;
            } else {
                isCmpFolding = true;
            }
            return isCmpFolding;
        }

        boolean unfolding(float rot) {
            boolean isCmpUnfolding = false;
            float targetRot = (float) McheliMath.getRotate360(rot + this.baseRotation);
            float prevRot = this.rotation;
            if (targetRot <= 180.0F) {
                this.rotation = (float) McheliMath.getRotate360(this.rotation + this.foldSpeed);
                if (this.rotation >= targetRot && prevRot <= targetRot) {
                    this.rotation = targetRot;
                    isCmpUnfolding = true;
                }
            } else {
                this.rotation = (float) McheliMath.getRotate360(this.rotation - this.foldSpeed);
                if (this.rotation <= targetRot && prevRot >= targetRot) {
                    this.rotation = targetRot;
                    isCmpUnfolding = true;
                }
            }
            return isCmpUnfolding;
        }
    }
}
