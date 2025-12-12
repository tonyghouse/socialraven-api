// Create this new service
package com.ghouse.socialraven.service.session;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class OAuthSessionService {
    
    // Store: sessionId -> { state, verifier, timestamp }
    private final Map<String, OAuthSession> sessions = new ConcurrentHashMap<>();
    
    public String createSession(String state, String verifier) {
        String sessionId = java.util.UUID.randomUUID().toString();
        
        OAuthSession session = new OAuthSession();
        session.setState(state);
        session.setVerifier(verifier);
        session.setCreatedAt(System.currentTimeMillis());
        
        sessions.put(sessionId, session);
        
        // Clean up old sessions (older than 10 minutes)
        cleanupOldSessions();
        
        return sessionId;
    }
    
    public OAuthSession getSession(String sessionId) {
        OAuthSession session = sessions.get(sessionId);
        
        if (session == null) {
            return null;
        }
        
        // Check if expired (10 minutes)
        long age = System.currentTimeMillis() - session.getCreatedAt();
        if (age > TimeUnit.MINUTES.toMillis(10)) {
            sessions.remove(sessionId);
            return null;
        }
        
        return session;
    }
    
    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }
    
    private void cleanupOldSessions() {
        long now = System.currentTimeMillis();
        long maxAge = TimeUnit.MINUTES.toMillis(10);
        
        sessions.entrySet().removeIf(entry -> 
            (now - entry.getValue().getCreatedAt()) > maxAge
        );
    }
    
    public static class OAuthSession {
        private String state;
        private String verifier;
        private long createdAt;
        
        // Getters and setters
        public String getState() { return state; }
        public void setState(String state) { this.state = state; }
        
        public String getVerifier() { return verifier; }
        public void setVerifier(String verifier) { this.verifier = verifier; }
        
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }
}