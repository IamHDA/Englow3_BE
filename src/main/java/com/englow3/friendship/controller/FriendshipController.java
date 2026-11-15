package com.englow3.friendship.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.englow3.friendship.dto.request.SendFriendRequestRequest;
import com.englow3.friendship.dto.response.CountResponse;
import com.englow3.friendship.dto.response.FriendRequestOutcomeResponse;
import com.englow3.friendship.dto.response.FriendRequestResponse;
import com.englow3.friendship.dto.response.FriendResponse;
import com.englow3.friendship.dto.response.LearnerSearchResponse;
import com.englow3.friendship.dto.response.RelationshipResponse;
import com.englow3.friendship.dto.result.FriendRequestOutcome;
import com.englow3.friendship.service.FriendshipService;
import com.englow3.shared.page.PageResponse;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/** A learner's friends and friend requests. Whether the caller is a learner is the service's to say. */
@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private static final int MAX_PAGE_SIZE = 50;

    private final FriendshipService friendshipService;

    public FriendshipController(FriendshipService friendshipService) {
        this.friendshipService = friendshipService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FriendResponse>> friends(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity
                .ok(PageResponse.from(friendshipService.friends(clamp(pageable)).map(FriendResponse::from)));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<LearnerSearchResponse>> search(@RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(friendshipService.searchLearners(q, clamp(pageable)).map(LearnerSearchResponse::from)));
    }

    /** How the caller stands with one learner, for a "Add friend" button on a page about that learner. */
    @GetMapping("/relationships/{userId}")
    public ResponseEntity<RelationshipResponse> relationship(@PathVariable UUID userId) {
        return ResponseEntity.ok(RelationshipResponse.from(friendshipService.relationshipWith(userId)));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<PageResponse<FriendRequestResponse>> incoming(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(friendshipService.incomingRequests(clamp(pageable)).map(FriendRequestResponse::from)));
    }

    @GetMapping("/requests/incoming/count")
    public ResponseEntity<CountResponse> incomingCount() {
        return ResponseEntity.ok(new CountResponse(friendshipService.incomingRequestCount()));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<PageResponse<FriendRequestResponse>> outgoing(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse
                .from(friendshipService.outgoingRequests(clamp(pageable)).map(FriendRequestResponse::from)));
    }

    @PostMapping("/requests")
    @ApiResponse(responseCode = "200", description = "The request already existed, or answered one the other learner sent")
    @ApiResponse(responseCode = "201", description = "A new request was created")
    public ResponseEntity<FriendRequestOutcomeResponse> sendRequest(
            @Valid @RequestBody SendFriendRequestRequest request) {
        FriendRequestOutcome outcome = friendshipService.sendRequest(request.toCommand());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(FriendRequestOutcomeResponse.from(outcome.request()));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<FriendRequestOutcomeResponse> accept(@PathVariable UUID requestId) {
        return ResponseEntity.ok(FriendRequestOutcomeResponse.from(friendshipService.accept(requestId).request()));
    }

    /** Declines a request sent to the caller, or cancels one the caller sent. */
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<Void> withdraw(@PathVariable UUID requestId) {
        friendshipService.withdraw(requestId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unfriend(@PathVariable UUID userId) {
        friendshipService.unfriend(userId);
        return ResponseEntity.noContent().build();
    }

    /** Page number and size only: the order of a friends list is not the client's to choose. */
    private static Pageable clamp(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
    }
}
