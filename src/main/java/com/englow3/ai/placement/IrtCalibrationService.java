package com.englow3.ai.placement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.englow3.shared.error.BadRequestException;
import com.englow3.shared.error.ConflictException;
import com.englow3.shared.error.NotFoundException;
import com.englow3.user.service.UserDirectory;

@Service
class IrtCalibrationService {

    private final JdbcTemplate jdbcTemplate;
    private final UserDirectory userDirectory;

    IrtCalibrationService(JdbcTemplate jdbcTemplate, UserDirectory userDirectory) {
        this.jdbcTemplate = jdbcTemplate;
        this.userDirectory = userDirectory;
    }

    @Transactional
    AdaptivePlacementDtos.CalibrationResponse importVersion(AdaptivePlacementDtos.CalibrationImportRequest request) {
        UUID actorId = requireUserId();
        Set<String> ids = new HashSet<>();
        for (AdaptivePlacementDtos.CalibrationItem item : request.items()) {
            String id = item.itemId().strip();
            if (!ids.add(id)) {
                throw new BadRequestException("IRT_CALIBRATION_DUPLICATE_ITEM",
                        "A calibration import cannot contain duplicate item IDs");
            }
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        Integer approved = jdbcTemplate.queryForObject("select count(*) from exam_items where item_id in ("
                + placeholders + ") and review_status = 'human_approved'", Integer.class, ids.toArray());
        if (approved == null || approved != ids.size()) {
            throw new BadRequestException("IRT_CALIBRATION_ITEMS_NOT_APPROVED",
                    "Every calibrated item must exist and be human approved");
        }
        String sourceHash = sourceHash(request);
        try {
            jdbcTemplate.update("""
                    insert into irt_calibration_versions
                        (version, source_hash, minimum_responses, status, created_by)
                    values (?, ?, ?, 'DRAFT', ?)
                    """, request.version(), sourceHash, request.minimumResponses(), actorId);
            request.items().stream().sorted(Comparator.comparing(AdaptivePlacementDtos.CalibrationItem::itemId))
                    .forEach(item -> jdbcTemplate.update("""
                            insert into irt_item_parameters
                                (calibration_version, item_id, discrimination, difficulty, guessing,
                                 response_count, standard_error)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """, request.version(), item.itemId().strip(), item.discrimination(), item.difficulty(),
                            item.guessing(), item.responseCount(), item.standardError()));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("IRT_CALIBRATION_VERSION_EXISTS",
                    "The calibration version or its immutable source already exists");
        }
        audit(actorId, "IRT_CALIBRATION_IMPORT", request.version(), sourceHash);
        return response(request.version());
    }

    @Transactional
    AdaptivePlacementDtos.CalibrationResponse activate(int version) {
        UUID actorId = requireUserId();
        CalibrationVersion calibration = jdbcTemplate.query("""
                select status, created_by, minimum_responses, source_hash
                from irt_calibration_versions where version = ? for update
                """,
                rs -> rs.next()
                        ? new CalibrationVersion(rs.getString("status"), rs.getObject("created_by", UUID.class),
                                rs.getInt("minimum_responses"), rs.getString("source_hash"))
                        : null,
                version);
        if (calibration == null) {
            throw new NotFoundException("IRT_CALIBRATION_NOT_FOUND", "The calibration version was not found");
        }
        if (!"DRAFT".equals(calibration.status())) {
            throw new ConflictException("IRT_CALIBRATION_NOT_DRAFT", "Only a draft calibration can be activated");
        }
        if (actorId.equals(calibration.createdBy())) {
            throw new ConflictException("IRT_CALIBRATION_FOUR_EYES_REQUIRED",
                    "A different administrator must activate the imported calibration");
        }
        Integer eligible = jdbcTemplate.queryForObject("""
                select count(*)
                from irt_item_parameters p
                join exam_items i on i.item_id = p.item_id
                where p.calibration_version = ? and p.response_count >= ?
                  and i.review_status = 'human_approved'
                """, Integer.class, version, calibration.minimumResponses());
        if (eligible == null || eligible < 5) {
            throw new ConflictException("IRT_CALIBRATION_POOL_TOO_SMALL",
                    "At least five approved items must meet the minimum response policy");
        }
        jdbcTemplate.update("update irt_calibration_versions set status = 'ARCHIVED' where status = 'ACTIVE'");
        jdbcTemplate.update("""
                update irt_calibration_versions
                set status = 'ACTIVE', activated_by = ?, activated_at = now()
                where version = ?
                """, actorId, version);
        audit(actorId, "IRT_CALIBRATION_ACTIVATE", version, calibration.sourceHash());
        return response(version);
    }

    @Transactional(readOnly = true)
    List<AdaptivePlacementDtos.CalibrationResponse> versions() {
        return jdbcTemplate.query("""
                select v.version, v.status, v.minimum_responses, v.source_hash, count(p.item_id) item_count
                from irt_calibration_versions v
                left join irt_item_parameters p on p.calibration_version = v.version
                group by v.version order by v.version desc
                """,
                (rs, row) -> new AdaptivePlacementDtos.CalibrationResponse(rs.getInt("version"), rs.getString("status"),
                        rs.getInt("minimum_responses"), rs.getInt("item_count"), rs.getString("source_hash")));
    }

    private AdaptivePlacementDtos.CalibrationResponse response(int version) {
        return jdbcTemplate.query("""
                select v.version, v.status, v.minimum_responses, v.source_hash, count(p.item_id) item_count
                from irt_calibration_versions v
                left join irt_item_parameters p on p.calibration_version = v.version
                where v.version = ? group by v.version
                """, rs -> {
            if (!rs.next()) {
                throw new NotFoundException("IRT_CALIBRATION_NOT_FOUND", "The calibration version was not found");
            }
            return new AdaptivePlacementDtos.CalibrationResponse(rs.getInt("version"), rs.getString("status"),
                    rs.getInt("minimum_responses"), rs.getInt("item_count"), rs.getString("source_hash"));
        }, version);
    }

    static String sourceHash(AdaptivePlacementDtos.CalibrationImportRequest request) {
        StringBuilder canonical = new StringBuilder().append(request.version()).append('|')
                .append(request.minimumResponses());
        request.items().stream().sorted(Comparator.comparing(AdaptivePlacementDtos.CalibrationItem::itemId))
                .forEach(item -> canonical.append('\n').append(item.itemId().strip()).append('|')
                        .append(Double.toHexString(item.discrimination())).append('|')
                        .append(Double.toHexString(item.difficulty())).append('|')
                        .append(Double.toHexString(item.guessing())).append('|').append(item.responseCount())
                        .append('|')
                        .append(item.standardError() == null ? "" : Double.toHexString(item.standardError())));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void audit(UUID actorId, String action, int version, String sourceHash) {
        jdbcTemplate.update("""
                insert into ai_admin_audit_log (id, actor_user_id, action, target_type, target_id, details)
                values (?, ?, ?, 'IRT_CALIBRATION', ?, jsonb_build_object('sourceHash', ?))
                """, UUID.randomUUID(), actorId, action, Integer.toString(version), sourceHash);
    }

    private UUID requireUserId() {
        return userDirectory.requireCurrentUserId();
    }

    private record CalibrationVersion(String status, UUID createdBy, int minimumResponses, String sourceHash) {
    }
}
