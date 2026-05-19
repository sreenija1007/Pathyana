package com.pathyana.api.service.profile;

import com.pathyana.api.dto.ProfileSeedRequest;
import com.pathyana.api.dto.ProfileSeedRequest.MedicationSeed;
import com.pathyana.api.model.*;
import com.pathyana.api.repository.UserRepository;
import com.pathyana.api.service.RxNormService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Profile management.
 *
 * v2 — uses RxNormService to normalize medication brand names to generic names
 * before saving. Improves rule-engine hit rate when users type brand names.
 *
 * If RxNorm can't resolve a name, we fall back to the lowercased displayName.
 * The user doesn't see a failure; the UI shows an "advisory only" badge.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final RxNormService rxNormService;

    public ProfileService(UserRepository userRepository, RxNormService rxNormService) {
        this.userRepository = userRepository;
        this.rxNormService = rxNormService;
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
            } catch (IllegalArgumentException ignored) {}
        }
        if (req.mealToBedBufferMinutes() != null) user.setMealToBedBufferMinutes(req.mealToBedBufferMinutes());
        if (req.typicalBedtime() != null) user.setTypicalBedtime(req.typicalBedtime());

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
                med.setGenericName(resolveGenericName(m));
                med.setDosage(m.dosage());
                med.setActive(true);
                user.getMedications().add(med);
            }
        }

        user.setProfileCompleted(true);
        return userRepository.save(user);
    }

    private String resolveGenericName(MedicationSeed seed) {
        if (seed.genericName() != null && !seed.genericName().isBlank()) {
            return seed.genericName().toLowerCase().trim();
        }
        if (seed.displayName() == null) return "";

        Optional<String> resolved = rxNormService.resolveToGeneric(seed.displayName());
        if (resolved.isPresent()) {
            log.info("RxNorm normalized '{}' -> '{}'", seed.displayName(), resolved.get());
            return resolved.get();
        }
        log.info("RxNorm could not resolve '{}', using lowercased fallback", seed.displayName());
        return seed.displayName().toLowerCase().trim();
    }
}
