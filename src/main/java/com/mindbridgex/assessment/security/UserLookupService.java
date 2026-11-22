package com.mindbridgex.assessment.security;

import com.mindbridgex.assessment.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class UserLookupService {
    public static final String CACHE = "authSnapshot";
    private final UserRepository users;

    /**
     * Cached ~30s (see spring.cache.caffeine.spec); evicted whenever a user is changed on this node.
     */
    @Cacheable(cacheNames = CACHE, key = "#userId", unless = "#result == null")
    @Transactional(readOnly = true)
    public AuthSnapshot snapshot(long userId) {
        return users.findById(userId).map(u -> new AuthSnapshot(u.getId(), u.getUsername(), u.getFullName(), u.isActive(), u.getTokenVersion(), u.isMustChangePassword(), Set.copyOf(u.getRoles()))).orElse(null);
    }

    @CacheEvict(cacheNames = CACHE, allEntries = true)
    public void evictAll() {
        // eviction handled by annotation
    }

    public UserLookupService(final UserRepository users) {
        this.users = users;
    }
}
