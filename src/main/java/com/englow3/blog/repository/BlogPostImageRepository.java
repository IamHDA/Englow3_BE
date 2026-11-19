package com.englow3.blog.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.englow3.blog.entity.BlogPostImage;

public interface BlogPostImageRepository extends JpaRepository<BlogPostImage, UUID> {

    List<BlogPostImage> findByIdIn(Collection<UUID> ids);

    List<BlogPostImage> findByPostId(UUID postId);

    /** Read before a post is deleted: deleting the post removes the rows by cascade but never the objects. */
    @Query("select i.objectKey from BlogPostImage i where i.postId = :post")
    List<String> findKeysByPostId(@Param("post") UUID postId);

    /**
     * Gives the post the images that are still nobody's and were uploaded by {@code uploaderId}, and returns how many
     * it took. Conditional, so two posts saved at once that name the same image cannot both get it: the second finds it
     * taken and takes none. The caller compares the count with how many it expected. Flushes first because the post it
     * names may not have been written yet.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update BlogPostImage i set i.postId = :post
            where i.id in :ids and i.postId is null and i.uploaderId = :uploader
            """)
    int attach(@Param("post") UUID postId, @Param("ids") Collection<UUID> ids, @Param("uploader") UUID uploaderId);

    List<BlogPostImage> findTop100ByPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(Instant before);

    /**
     * 1 when this call removed the row. Whoever gets 1 owns the object; a post that took the image in between gets 0
     * here.
     */
    @Modifying
    @Query("delete from BlogPostImage i where i.id = :id and i.postId is null")
    int deleteIfUnattached(@Param("id") UUID id);
}
