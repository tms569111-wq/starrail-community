package com.starrailhearing.comment.service;

import com.starrailhearing.character.domain.GameCharacter;
import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.comment.repository.CommentLikeRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.evaluation.domain.CharacterEvaluation;
import com.starrailhearing.evaluation.domain.GameVersion;
import com.starrailhearing.evaluation.domain.VersionStatus;
import com.starrailhearing.evaluation.repository.EvaluationReader;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.member.service.BadgeView;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.domain.VerifiedCharacter;
import com.starrailhearing.profile.repository.VerifiedCharacterRepository;
import com.starrailhearing.vote.service.EidolonFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommentService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE_NUMBER = 100;
    private static final long MAX_ROOT_COMMENTS_PER_CHARACTER = 10;

    private final MemberService memberService;
    private final VerifiedCharacterRepository verifiedCharacterRepository;
    private final EvaluationReader evaluationReader;
    private final CharacterCommentRepository commentRepository;
    private final CommentLikeRepository likeRepository;
    private final BadgeService badgeService;
    private final Clock clock;

    public CommentService(
            MemberService memberService,
            VerifiedCharacterRepository verifiedCharacterRepository,
            EvaluationReader evaluationReader,
            CharacterCommentRepository commentRepository,
            CommentLikeRepository likeRepository,
            BadgeService badgeService,
            Clock clock
    ) {
        this.memberService = memberService;
        this.verifiedCharacterRepository = verifiedCharacterRepository;
        this.evaluationReader = evaluationReader;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
        this.badgeService = badgeService;
        this.clock = clock;
    }

    @Transactional
    public EidolonFilter create(
            long memberId,
            GameCharacter character,
            GameVersion version,
            String content
    ) {
        MemberAccount member = memberService.requireActiveForWriteLocked(memberId);
        VerifiedCharacter verified = requireVerified(memberId, character.getId());
        CharacterEvaluation evaluation = evaluationReader.requireEvaluation(
                character.getId(), version.getId()
        );
        requireOpenVersion(evaluation);
        requireRootCommentLimit(memberId, evaluation);
        requireWriteInterval(memberId, evaluation.getId());
        save(() -> new CharacterComment(member, evaluation, verified, content));
        return EidolonFilter.forEidolon(verified.getEidolon());
    }

    @Transactional
    public EidolonFilter createReply(
            long memberId,
            GameCharacter character,
            GameVersion version,
            long parentId,
            String content
    ) {
        MemberAccount member = memberService.requireActiveForWriteLocked(memberId);
        CharacterComment parent = requireActive(parentId);
        requireCharacter(character.getId(), parent);
        requireVersion(version, parent);
        if (!parent.isRoot()) throw new AppException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        VerifiedCharacter verified = requireVerified(memberId, character.getId());
        CharacterEvaluation evaluation = parent.getEvaluation();
        requireOpenVersion(evaluation);
        requireWriteInterval(memberId, evaluation.getId());
        save(() -> new CharacterComment(member, evaluation, verified, parent, content));
        return EidolonFilter.forEidolon(verified.getEidolon());
    }

    @Transactional
    public void edit(long memberId, long commentId, Long expectedCharacterId, String content) {
        memberService.requireActiveForWrite(memberId);
        CharacterComment comment = requireActive(commentId);
        requireCharacter(expectedCharacterId, comment);
        requireOpenVersion(comment.getEvaluation());
        requireOwner(memberId, comment);
        VerifiedCharacter verified = requireVerified(
                memberId, comment.getEvaluation().getCharacter().getId()
        );
        try {
            comment.edit(content, verified);
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
    }

    @Transactional
    public void delete(long memberId, long commentId, Long expectedCharacterId) {
        memberService.requireActiveForWrite(memberId);
        CharacterComment comment = requireActive(commentId);
        requireCharacter(expectedCharacterId, comment);
        requireOpenVersion(comment.getEvaluation());
        requireOwner(memberId, comment);
        comment.deleteByAuthor(LocalDateTime.now(clock));
    }

    @Transactional
    public boolean toggleLike(long memberId, long commentId, Long expectedCharacterId) {
        memberService.requireActiveForWrite(memberId);
        CharacterComment comment = requireActive(commentId);
        requireCharacter(expectedCharacterId, comment);
        requireOpenVersion(comment.getEvaluation());
        if (comment.getMember().getId().equals(memberId)) {
            throw new AppException(ErrorCode.SELF_LIKE_NOT_ALLOWED);
        }

        if (likeRepository.deleteByMember_IdAndComment_Id(memberId, commentId) == 1) {
            commentRepository.decrementLikeCount(commentId);
            return false;
        }
        if (likeRepository.insertIgnore(memberId, commentId) == 1) {
            commentRepository.incrementLikeCount(commentId);
        }
        return true;
    }

    public CommentPageView view(
            Long memberId,
            GameCharacter character,
            GameVersion version,
            EidolonFilter filter,
            CommentSort sort,
            int requestedPage
    ) {
        CharacterEvaluation evaluation = evaluationReader.requireEvaluation(
                character.getId(), version.getId()
        );
        int pageNumber = boundedPageNumber(requestedPage);
        Page<CharacterComment> page = commentRepository.findVisibleRoots(
                evaluation.getId(), filter.getMinimum(), filter.getMaximum(),
                PageRequest.of(pageNumber, PAGE_SIZE, sort.toSort())
        );
        Page<CharacterComment> bestPage = commentRepository.findVisibleRoots(
                evaluation.getId(), filter.getMinimum(), filter.getMaximum(),
                PageRequest.of(0, 3,
                        Sort.by(Sort.Order.desc("likeCount"), Sort.Order.asc("createdAt")))
        );

        List<CharacterComment> allVisible = new ArrayList<>(page.getContent());
        allVisible.addAll(bestPage.getContent());
        Set<Long> likedIds = memberId == null ? Set.of() : likedIds(memberId, allVisible);
        Map<Long, Long> replyCounts = replyCounts(allVisible);
        VerifiedCharacter verified = memberId == null ? null : verifiedCharacterRepository
                .findByProfile_Member_IdAndCharacter_Id(memberId, character.getId())
                .orElse(null);
        MemberAccount viewer = memberId == null ? null : memberService.requireReadable(memberId);
        boolean writerEligible = viewer != null
                && viewer.isActive()
                && viewer.isNicknameConfigured()
                && evaluation.getVersion().getStatus() == VersionStatus.OPEN;
        Map<Long, BadgeView> badges = badges(allVisible, evaluation.getGameVersion());

        return new CommentPageView(
                bestPage.getContent().stream()
                        .filter(comment -> comment.getStatus() == CommentStatus.ACTIVE)
                        .filter(comment -> comment.getLikeCount() > 0)
                        .map(comment -> toView(comment, memberId, likedIds, badges,
                                replyCounts.getOrDefault(comment.getId(), 0L)))
                        .toList(),
                page.getContent().stream()
                        .map(comment -> toView(comment, memberId, likedIds, badges,
                                replyCounts.getOrDefault(comment.getId(), 0L)))
                        .toList(),
                page.getNumber(),
                page.getTotalPages(),
                page.getTotalElements(),
                sort,
                memberId != null,
                writerEligible && verified != null,
                verified == null ? null : verified.getEidolon()
        );
    }

    static int boundedPageNumber(int requestedPage) {
        return Math.min(MAX_PAGE_NUMBER, Math.max(0, requestedPage));
    }

    public ReplyThreadView replies(
            Long memberId,
            GameCharacter character,
            GameVersion version,
            long parentId
    ) {
        CharacterComment parent = commentRepository.findDetailedById(parentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        requireCharacter(character.getId(), parent);
        requireVersion(version, parent);
        if (!parent.isRoot()) throw new AppException(ErrorCode.REPLY_DEPTH_EXCEEDED);
        List<CharacterComment> replies = commentRepository
                .findTop100ByParent_IdAndStatusOrderByCreatedAtAsc(parentId, CommentStatus.ACTIVE);
        Set<Long> likedIds = memberId == null ? Set.of() : likedIds(memberId, replies);
        Map<Long, BadgeView> badges = badges(replies, parent.getEvaluation().getGameVersion());
        List<CommentView> views = replies.stream()
                .map(reply -> toView(reply, memberId, likedIds, badges, 0))
                .toList();
        long total = commentRepository.countActiveReplies(List.of(parentId)).stream()
                .findFirst().map(CharacterCommentRepository.ReplyCount::getReplyCount).orElse(0L);
        return new ReplyThreadView(views, total);
    }

    public boolean canComment(Long memberId, Long characterId, GameVersion version) {
        if (memberId == null) return false;
        MemberAccount member = memberService.requireReadable(memberId);
        CharacterEvaluation evaluation = evaluationReader.requireEvaluation(
                characterId, version.getId()
        );
        return evaluation.getVersion().getStatus() == VersionStatus.OPEN
                && member.isActive()
                && member.isNicknameConfigured()
                && verifiedCharacterRepository
                        .findByProfile_Member_IdAndCharacter_Id(memberId, characterId)
                        .isPresent();
    }

    public boolean canWrite(Long memberId) {
        if (memberId == null) return false;
        MemberAccount member = memberService.requireReadable(memberId);
        return member.isActive() && member.isNicknameConfigured();
    }

    private void save(java.util.function.Supplier<CharacterComment> comment) {
        try {
            commentRepository.save(comment.get());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
    }

    private void requireRootCommentLimit(long memberId, CharacterEvaluation evaluation) {
        long count = commentRepository.countByMember_IdAndEvaluation_IdAndParentIsNullAndStatus(
                memberId,
                evaluation.getId(),
                CommentStatus.ACTIVE
        );
        if (count >= MAX_ROOT_COMMENTS_PER_CHARACTER) {
            throw new AppException(ErrorCode.COMMENT_COUNT_LIMIT);
        }
    }

    private void requireWriteInterval(long memberId, long evaluationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        commentRepository
                .findFirstByMember_IdAndEvaluation_IdOrderByCreatedAtDesc(memberId, evaluationId)
                .filter(comment -> comment.getCreatedAt().plusSeconds(20).isAfter(now))
                .ifPresent(comment -> {
                    throw new AppException(ErrorCode.COMMENT_RATE_LIMIT);
                });
    }

    private Map<Long, Long> replyCounts(List<CharacterComment> roots) {
        List<Long> ids = roots.stream().map(CharacterComment::getId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return commentRepository.countActiveReplies(ids).stream().collect(Collectors.toMap(
                CharacterCommentRepository.ReplyCount::getParentId,
                CharacterCommentRepository.ReplyCount::getReplyCount
        ));
    }

    private Map<Long, BadgeView> badges(List<CharacterComment> comments, String version) {
        List<Long> authorIds = comments.stream()
                .filter(comment -> comment.getStatus() == CommentStatus.ACTIVE)
                .map(comment -> comment.getMember().getId())
                .distinct()
                .toList();
        return badgeService.findForMembers(authorIds, version);
    }

    private Set<Long> likedIds(long memberId, List<CharacterComment> comments) {
        List<Long> ids = comments.stream()
                .filter(comment -> comment.getStatus() == CommentStatus.ACTIVE)
                .map(CharacterComment::getId)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(likeRepository.findLikedCommentIds(memberId, ids));
    }

    private CommentView toView(
            CharacterComment comment,
            Long memberId,
            Set<Long> likedIds,
            Map<Long, BadgeView> badges,
            long replyCount
    ) {
        boolean active = comment.getStatus() == CommentStatus.ACTIVE;
        Long authorId = active ? comment.getMember().getId() : null;
        return new CommentView(
                comment.getId(),
                authorId,
                active ? comment.getMember().getNickname() : "작성자 비공개",
                active ? badges.get(authorId) : null,
                comment.getEidolonAtWrite(),
                active ? comment.getContent() : placeholder(comment.getStatus()),
                comment.getLikeCount(),
                comment.getCreatedAt(),
                active && memberId != null && authorId.equals(memberId),
                active && likedIds.contains(comment.getId()),
                comment.getStatus(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                replyCount
        );
    }

    private String placeholder(CommentStatus status) {
        return status == CommentStatus.HIDDEN_BY_MODERATOR
                ? "운영 정책에 따라 숨김 처리된 댓글입니다."
                : "삭제된 댓글입니다.";
    }

    private CharacterComment requireActive(long commentId) {
        return commentRepository.findByIdAndStatus(commentId, CommentStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
    }

    private VerifiedCharacter requireVerified(long memberId, Long characterId) {
        return verifiedCharacterRepository
                .findByProfile_Member_IdAndCharacter_Id(memberId, characterId)
                .orElseThrow(() -> new AppException(ErrorCode.CHARACTER_NOT_VERIFIED));
    }

    private void requireOwner(long memberId, CharacterComment comment) {
        if (!comment.getMember().getId().equals(memberId)) {
            throw new AppException(ErrorCode.COMMENT_PERMISSION_DENIED);
        }
    }

    private void requireCharacter(Long expectedCharacterId, CharacterComment comment) {
        if (!comment.getEvaluation().getCharacter().getId().equals(expectedCharacterId)) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void requireVersion(GameVersion expectedVersion, CharacterComment comment) {
        if (!comment.getEvaluation().getVersion().getId().equals(expectedVersion.getId())) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }
    }

    private void requireOpenVersion(CharacterEvaluation evaluation) {
        if (evaluation.getVersion().getStatus() != VersionStatus.OPEN) {
            throw new AppException(
                    ErrorCode.VERSION_STATE_CONFLICT,
                    "종료된 버전의 댓글은 작성·수정·삭제·추천할 수 없습니다."
            );
        }
    }
}
