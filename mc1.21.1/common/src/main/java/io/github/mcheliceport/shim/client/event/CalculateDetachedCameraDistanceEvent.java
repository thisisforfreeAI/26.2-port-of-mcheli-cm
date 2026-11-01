package io.github.mcheliceport.shim.client.event;

/** How far the third-person camera sits from its entity. */
public final class CalculateDetachedCameraDistanceEvent extends ClientEvent {
    private float distance;

    public CalculateDetachedCameraDistanceEvent(float distance) {
        this.distance = distance;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }
}
