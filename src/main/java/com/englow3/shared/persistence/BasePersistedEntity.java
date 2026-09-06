package com.englow3.shared.persistence;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * A {@code UUID} primary key the entity assigns itself, and the one thing Spring Data cannot work out once it does.
 * <p>
 * {@code SimpleJpaRepository.save(...)} picks between {@code em.persist} and {@code em.merge} by asking
 * {@code isNew()}, whose default answer is "id is null". That heuristic is correct only when the database generates the
 * key: a fresh object has no id, a loaded one does. Every entity here assigns its own id in a factory instead - so a
 * row that has never been written looks exactly like a loaded one, {@code save()} takes the {@code merge} branch, and
 * Hibernate issues a {@code SELECT} per row before the {@code INSERT} it was always going to do. Harmless, and
 * invisible, until something saves a thousand rows at once (replacing a paper's content does).
 * <p>
 * {@code isNew} is {@code @Transient} and starts true; {@code @PostLoad} is what makes it honest for a row read back
 * from the database, without which saving a loaded entity would try to persist a detached one.
 * <p>
 * <b>Identity only.</b> The moment {@code createdAt}, {@code createdBy} or any other shared column lands here, this
 * stops being a technical type and becomes the base entity carrying business meaning that {@code shared} may not hold -
 * those columns belong to the entity that owns them, even when several entities happen to have one.
 */
@MappedSuperclass
public abstract class BasePersistedEntity implements Persistable<UUID> {

    /** Protected rather than private so a subclass factory can assign it the way it did before this class existed. */
    @Id
    protected UUID id;

    @Transient
    private boolean isNew = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
}
