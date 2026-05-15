package com.pathyana.api.service.profile;

import com.pathyana.api.dto.ProfileSeedRequest;
import com.pathyana.api.dto.ProfileSeedRequest.MedicationSeed;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing user profile data.
 *
 * For Phase 4, just one operation: replace a user's profile from a seed request.
 * Used by the dev "/api/profile/seed" endpoint to quickly populate test users.
 *
 * Phase 5 (later) will add granular endpoints: add allergy, remove medication, etc.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User seed(User user, ProfileSeedRequest req) {
        if (req.age() != null) user.setAge(req.age());
        if (req.city() != null) user.setCity(req.city());
        if (req.dietaryPreference() != null) user.setDietaryPreference(req.dietaryPreference());
        if (req.healthGoal() != null) user.setHealthGoal(req.healthGoal());
        if (req.cuisinePreference() != null) user.setCuisinePreference(req.cuisinePreference());

        if (req.eatingWindowStart() != null) user.setEatingWindowStart(req.eatingWindowStart());
        if (req.eatingWindowEnd() != null) user.setEatingWindowEnd(req.eatingWindowEnd());
        if (req.windowProtocol() != null) {
            try {
                user.setWindowProtocol(User.WindowProtocol.valueOf(req.windowProtocol().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // ignore invalid enum — leaves existing value
            }
        }
        if (req.mealToBedBufferMinutes() != null) user.setMealToBedBufferMinutes(req.mealToBedBufferMinutes());
        if (req.typicalBedtime() != null) user.setTypicalBedtime(req.typicalBedtime());

        // Replace collections wholesale. orphanRemoval=true on the entity deletes the old rows.
        if (req.healthConditions() != null) {
            user.getHealthConditions().clear();
            for (String c : req.healthConditions()) {
                HealthCondition hc = new HealthCondition();
                hc.setUser(user);
                hc.setCondition(c);
                user.getHealthConditions().add(hc);
            }
        }
        if (req.allergies() != null) {
            user.getAllergies().clear();
            for (String a : req.allergies()) {
                Allergy al = new Allergy();
                al.setUser(user);
                al.setAllergy(a);
                user.getAllergies().add(al);
            }
        }
        if (req.foodPreferences() != null) {
            user.getFoodPreferences().clear();
            for (String f : req.foodPreferences()) {
                FoodPreference fp = new FoodPreference();
                fp.setUser(user);
                fp.setFood(f);
                user.getFoodPreferences().add(fp);
            }
        }
        if (req.foodsToAvoid() != null) {
            user.getFoodsToAvoid().clear();
            for (String f : req.foodsToAvoid()) {
                FoodToAvoid fa = new FoodToAvoid();
                fa.setUser(user);
                fa.setFood(f);
                user.getFoodsToAvoid().add(fa);
            }
        }
        if (req.medications() != null) {
            user.getMedications().clear();
            for (MedicationSeed m : req.medications()) {
                Medication med = new Medication();
                med.setUser(user);
                med.setDisplayName(m.displayName());
                med.setGenericName(m.genericName() == null ? "" : m.genericName().toLowerCase().trim());
                med.setDosage(m.dosage());
                med.setActive(true);
                user.getMedications().add(med);
            }
        }

        user.setProfileCompleted(true);
        return userRepository.save(user);
    }
}
