package org.example.crop;

import java.util.List;
import java.util.Optional;

import org.example.model.CropProfile;

/**
 * Read access to crop profiles. Callers depend on this abstraction, not on where the
 * profiles are stored (Repository pattern, DIP).
 */
public interface CropRepository {

    Optional<CropProfile> findByName(String name);

    List<CropProfile> findAll();
}
