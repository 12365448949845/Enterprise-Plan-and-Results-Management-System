package com.planning.platform.dispute.controller;

import com.planning.platform.auth.service.AuthService;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.common.web.ApiResult;
import com.planning.platform.dispute.dto.DisputeDecisionReqDTO;
import com.planning.platform.dispute.dto.DisputeOpinionReqDTO;
import com.planning.platform.dispute.dto.DisputeRecusalReqDTO;
import com.planning.platform.dispute.dto.DisputeReviewerReqDTO;
import com.planning.platform.dispute.service.DisputeService;
import com.planning.platform.dispute.vo.DisputeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/dispute")
public class DisputeController {
    private final AuthService authService;
    private final DisputeService disputeService;

    @GetMapping("/dashboard")
    public ApiResult<DisputeVO.DashboardVO> dashboard(Authentication authentication) {
        return ApiResult.ok(disputeService.dashboard(user(authentication)));
    }

    @GetMapping("/cases")
    public ApiResult<List<DisputeVO.CaseItemVO>> cases(Authentication authentication,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String period,
                                                        @RequestParam(required = false) String keyword) {
        return ApiResult.ok(disputeService.cases(user(authentication), status, period, keyword));
    }

    @GetMapping("/cases/{id}")
    public ApiResult<DisputeVO.DetailVO> detail(Authentication authentication, @PathVariable Long id) {
        return ApiResult.ok(disputeService.detail(user(authentication), id));
    }

    @GetMapping("/cases/{id}/reviewers")
    public ApiResult<List<DisputeVO.ReviewerVO>> reviewers(Authentication authentication, @PathVariable Long id) {
        return ApiResult.ok(disputeService.reviewerList(user(authentication), id));
    }

    @GetMapping("/cases/{id}/reviewer-candidates")
    public ApiResult<List<DisputeVO.ReviewerCandidateVO>> reviewerCandidates(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(disputeService.reviewerCandidates(user(authentication), id, keyword));
    }

    @GetMapping("/cases/{id}/package")
    public ResponseEntity<Resource> packageDownload(Authentication authentication, @PathVariable Long id) {
        return disputeService.downloadPackage(user(authentication), id);
    }

    @PostMapping("/cases/{id}/reviewers")
    public ApiResult<DisputeVO.ReviewerVO> addReviewer(Authentication authentication,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody DisputeReviewerReqDTO request) {
        return ApiResult.ok(disputeService.addReviewer(user(authentication), id, request));
    }

    @DeleteMapping("/cases/{id}/reviewers/{reviewerId}")
    public ApiResult<Void> removeReviewer(Authentication authentication,
                                          @PathVariable Long id,
                                          @PathVariable Long reviewerId) {
        disputeService.removeReviewer(user(authentication), id, reviewerId);
        return ApiResult.ok(null);
    }

    @PostMapping("/cases/{id}/recusal")
    public ApiResult<Void> recuse(Authentication authentication,
                                  @PathVariable Long id,
                                  @Valid @RequestBody DisputeRecusalReqDTO request) {
        disputeService.recuse(user(authentication), id, request);
        return ApiResult.ok(null);
    }

    @PostMapping("/cases/{id}/opinions")
    public ApiResult<DisputeVO.OpinionVO> opinion(Authentication authentication,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody DisputeOpinionReqDTO request) {
        return ApiResult.ok(disputeService.saveOpinion(user(authentication), id, request));
    }

    @PostMapping("/cases/{id}/decision")
    public ApiResult<Void> decision(Authentication authentication,
                                    @PathVariable Long id,
                                    @Valid @RequestBody DisputeDecisionReqDTO request) {
        disputeService.decide(user(authentication), id, request);
        return ApiResult.ok(null);
    }

    private AuthUser user(Authentication authentication) {
        return authService.requireAuthUser(authentication);
    }
}
