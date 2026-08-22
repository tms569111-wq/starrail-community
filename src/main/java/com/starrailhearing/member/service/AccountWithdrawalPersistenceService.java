package com.starrailhearing.member.service;

import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.MemberBadgeRepository;
import com.starrailhearing.member.repository.MemberNicknameHistoryRepository;
import com.starrailhearing.member.repository.TitleVerificationRequestRepository;
import com.starrailhearing.profile.repository.GameProfileRepository;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AccountWithdrawalPersistenceService {
    private final MemberService memberService;
    private final MemberNicknameHistoryRepository nicknameHistoryRepository;
    private final MemberBadgeRepository badgeRepository;
    private final TitleVerificationRequestRepository titleRequestRepository;
    private final GameProfileRepository profileRepository;
    private final CharacterVoteRepository voteRepository;
    private final CharacterCommentRepository commentRepository;
    private final CommentLikeRepository likeRepository;
    private final TitleImageStorage imageStorage;
    private final Clock clock;

    public AccountWithdrawalPersistenceService(
            MemberService memberService,
            MemberNicknameHistoryRepository nicknameHistoryRepository,
            MemberBadgeRepository badgeRepository,
            TitleVerificationRequestRepository titleRequestRepository,
            GameProfileRepository profileRepository,
            CharacterVoteRepository voteRepository,
            CharacterCommentRepository commentRepository,
            CommentLikeRepository likeRepository,
            TitleImageStorage imageStorage,
            Clock clock
    ) {
        this.memberService = memberService;
        this.nicknameHistoryRepository = nicknameHistoryRepository;
        this.badgeRepository = badgeRepository;
        this.titleRequestRepository = titleRequestRepository;
        this.profileRepository = profileRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    @Transactional
    public void withdrawData(long memberId) {
        MemberAccount member = memberService.requireReadableForUpdate(memberId);
        boolean evidenceDeleted = true;
        for (var request : titleRequestRepository.findAllByMember_IdOrderByCreatedAtDesc(memberId)) {
            String path = request.getPrivateImagePath();
            if (path != null && !path.isBlank()) {
                evidenceDeleted = imageStorage.delete(path) && evidenceDeleted;
            }
        }
        if (!evidenceDeleted) throw new AppException(ErrorCode.ACCOUNT_WITHDRAWAL_FAILED);

        commentRepository.decrementLikesByMember(memberId);
        likeRepository.deleteAllByMember_Id(memberId);
        voteRepository.deleteByMember_Id(memberId);
        titleRequestRepository.deleteAllByMember_Id(memberId);
        badgeRepository.deleteAllByMember_Id(memberId);
        nicknameHistoryRepository.deleteAllByMember_Id(memberId);
        profileRepository.deleteByMember_Id(memberId);
        member.withdraw(LocalDateTime.now(clock));
    }
}
