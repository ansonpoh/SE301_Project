package org.example.model;

import java.util.Objects;

/**
 * A growing chamber: its identifier, the crop it grows and its live environment.
 * Each chamber owns its {@link ChamberState}, so no global lookup map is needed.
 */
public final class Chamber {

    private final String id;
    private final CropProfile crop;
    private final ChamberState state;

    public Chamber(String id, CropProfile crop, ChamberState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.crop = Objects.requireNonNull(crop, "crop");
        this.state = Objects.requireNonNull(state, "state");
    }

    public String id() {
        return id;
    }

    public CropProfile crop() {
        return crop;
    }

    public ChamberState state() {
        return state;
    }
}
