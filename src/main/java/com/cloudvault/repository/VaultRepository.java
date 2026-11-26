package com.cloudvault.repository;

import jakarta.persistence.*;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class VaultRepository {
    @PersistenceContext private EntityManager em;
    public <T> T find(Class<T> type, Long id) { return id == null ? null : em.find(type, id); }
    public <T> T lock(Class<T> type, Long id) { return em.find(type, id, LockModeType.PESSIMISTIC_WRITE); }
    public void add(Object entity) { em.persist(entity); }
    public void remove(Object entity) { em.remove(entity); }
    public void flush() { em.flush(); }
    public <T> List<T> list(Class<T> type, String jpql, Object... args) {
        TypedQuery<T> query = em.createQuery(jpql, type);
        for (int i=0; i<args.length; i++) query.setParameter(i+1,args[i]);
        return query.getResultList();
    }
    public <T> Optional<T> first(Class<T> type, String jpql, Object... args) {
        return list(type,jpql,args).stream().findFirst();
    }
    public int execute(String jpql, Object... args) {
        Query query = em.createQuery(jpql);
        for (int i=0; i<args.length; i++) query.setParameter(i+1,args[i]);
        return query.executeUpdate();
    }
}
