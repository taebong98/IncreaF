package com.main19.server.posting.service;

import com.main19.server.auth.jwt.JwtTokenizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.main19.server.member.entity.Member;
import com.main19.server.member.service.MemberService;
import com.main19.server.posting.dto.PostingPatchDto;
import com.main19.server.posting.dto.PostingPostDto;
import com.main19.server.posting.mapper.PostingMapper;
import com.main19.server.posting.tags.entity.PostingTags;
import com.main19.server.posting.tags.service.PostingTagsService;
import com.main19.server.posting.tags.service.TagService;
import com.main19.server.s3service.S3StorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.main19.server.exception.BusinessLogicException;
import com.main19.server.exception.ExceptionCode;
import com.main19.server.posting.entity.Media;
import com.main19.server.posting.entity.Posting;
import com.main19.server.posting.repository.MediaRepository;
import com.main19.server.posting.repository.PostingRepository;
import com.main19.server.utils.CustomBeanUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PostingService {
	private final PostingRepository postingRepository;
	private final MediaRepository mediaRepository;
	private final MemberService memberService;
	private final TagService tagService;
	private final PostingTagsService postingTagsService;
	private final S3StorageService storageService;
	private final PostingMapper mapper;
	private final CustomBeanUtils<Posting> beanUtils;
	private final JwtTokenizer jwtTokenizer;

	public Posting createPosting(PostingPostDto requestBody, long memberId, List<MultipartFile> multipartFiles, String token) {

		if (memberId != jwtTokenizer.getMemberId(token)) {
			throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
		}

		List<String> mediaPaths = storageService.uploadMedia(multipartFiles);

		Posting posting = mapper.postingPostDtoToPosting(requestBody);

		Member findMember = memberService.findMember(memberId);
		posting.setMember(findMember);

		posting.setCreatedAt(LocalDateTime.now());

		for (String mediaUrl: mediaPaths) {
			Media media = new Media(mediaUrl, posting);
			mediaRepository.save(media);
			posting.getPostingMedias().add(media);
		}

		for(int i = 0; i < requestBody.getTagName().size(); i++) {
			tagService.createTag(mapper.tagPostDtoToTag(requestBody.getTagName().get(i)));
			PostingTags postingTags = mapper.postingPostDtoToPostingTag(requestBody);
			String tagName = requestBody.getTagName().get(i);
			postingTagsService.createPostingTags(postingTags, posting, tagName);
		}

		return postingRepository.save(posting);
	}

	public Posting updatePosting(PostingPatchDto requestBody, String token) {

		Posting posting = mapper.postingPatchDtoToPosting(requestBody);
		Posting findPosting = findVerifiedPosting(posting.getPostingId());

		if (findPosting.getMemberId() != jwtTokenizer.getMemberId(token)) {
			throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
		}

		Posting updatePosting = beanUtils.copyNonNullProperties(posting, findPosting);

		updatePosting.setModifiedAt(LocalDateTime.now());

		for (int i = 0; i < requestBody.getTagName().size(); i++) {
			tagService.createTag(mapper.tagPostDtoToTag(requestBody.getTagName().get(i)));
			PostingTags postingTags = mapper.postingPatchDtoToPostingTag(requestBody);
			String tagName = requestBody.getTagName().get(i);
			postingTagsService.updatePostingTags(postingTags, updatePosting, tagName);
		}

		return postingRepository.save(updatePosting);
	}

	@Transactional(readOnly = true)
	public Posting findPosting(long postingId) {
		Posting findPosting = findVerifiedPosting(postingId);
		return findPosting;
	}

	@Transactional(readOnly = true)
	public Page<Posting> findPostings(int page, int size) {
		return postingRepository.findAll(PageRequest.of(page, size, Sort.by("postingId").descending()));
	}

	@Transactional(readOnly = true)
	public Page<Posting> findPostingsByMemberId(long memberId, int page, int size) {
		return postingRepository.findByMember_MemberId(memberId, PageRequest.of(page, size, Sort.by("postingId").descending()));
	}

	public void deletePosting(long postingId, String token) {
		Posting findPosting = findVerifiedPosting(postingId);

		if (findPosting.getMember().getMemberId() != jwtTokenizer.getMemberId(token)) {
			throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
		}

		storageService.removeAll(findPosting);
		postingRepository.delete(findPosting);
	}

	public Posting addMedia(long postingId, List<MultipartFile> multipartFiles, String token) {
		Posting findPosting = findVerifiedPosting(postingId);

		if (findPosting.getMember().getMemberId() != jwtTokenizer.getMemberId(token)) {
			throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
		}

		countMedias(findPosting, multipartFiles);

		List<String> mediaPaths = storageService.uploadMedia(multipartFiles);

		for (String mediaUrl: mediaPaths) {
			Media media = new Media(mediaUrl, findPosting);
			mediaRepository.save(media);
			findPosting.getPostingMedias().add(media);
		}

		return findPosting;
	}

	public void deleteMedia(long mediaId, String token) {
		Posting posting = findVerfiedMedia(mediaId).getPosting();

		if (posting.getMember().getMemberId() != jwtTokenizer.getMemberId(token)) {
			throw new BusinessLogicException(ExceptionCode.FORBIDDEN);
		}

		if (posting.getPostingMedias().stream().count() == 1) {
			throw new BusinessLogicException(ExceptionCode.POSTING_MEDIA_ERROR);
		}

		storageService.remove(mediaId);

		Media findMedia = findVerfiedMedia(mediaId);
		mediaRepository.delete(findMedia);
	}

	@Transactional(readOnly = true)
	public Posting findVerifiedPosting(long postingId) {
		Optional<Posting> optionalPosting = postingRepository.findById(postingId);
		Posting findPosting =
			optionalPosting.orElseThrow(() ->
				new BusinessLogicException(ExceptionCode.POSTING_NOT_FOUND));
		return findPosting;
	}

	@Transactional(readOnly = true)
	public Media findVerfiedMedia(long mediaId) {
		Optional<Media> optionalMedia = mediaRepository.findById(mediaId);
		Media findMedia =
			optionalMedia.orElseThrow(() ->
				new BusinessLogicException(ExceptionCode.MEDIA_NOT_FOUND));
		return findMedia;
	}

	@Transactional(readOnly = true)
	private void countMedias(Posting findPosting, List<MultipartFile> multipartFiles) {
		int cntMultipartFiles = 0;
		if (multipartFiles.get(1) == null) {
			cntMultipartFiles = 1;
		} else cntMultipartFiles = 2;

		if (findPosting.getPostingMedias().size() + cntMultipartFiles > 3) {
			throw new BusinessLogicException(ExceptionCode.POSTING_MEDIA_ERROR);
		}
	}
}
