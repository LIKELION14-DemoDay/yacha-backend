package likelion.yacha_backend.domain.philosophy.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import likelion.yacha_backend.domain.ai.AiException;
import likelion.yacha_backend.domain.ai.PhilosopherWriting;
import likelion.yacha_backend.domain.ai.PhilosopherWriting.Section;
import likelion.yacha_backend.domain.ai.PhilosophyWriter;
import likelion.yacha_backend.domain.philosophy.entity.PhilosopherContent;
import likelion.yacha_backend.domain.philosophy.entity.PhilosophySection;

/**
 * AI가 만든 1분 철학 글이 저장할 수 있는 모양인지 확인하고, 앞뒤 공백을 정리해 돌려줌
 *
 * 맞지 않으면 다시 시도할 수 있는 {@link AiException}을 던짐
 * 같은 주제로 다시 만들면 대개 맞게 나오므로, 부르는 쪽은 AI 호출 실패와 똑같이 재시도하면 됨
 *
 * 길이는 AI가 정확히 맞추지 못해 목표({@value PhilosophyWriter#TARGET_MIN_LENGTH}~{@value PhilosophyWriter#TARGET_MAX_LENGTH}자)보다
 * 여유를 둠. 사람이 검토하므로 여기서는 화면이 깨질 정도인지만 막음
 */
public final class PhilosophyWritingValidator {

    /** 철학자 한 명의 내용 글자 수 하한 · 상한 (공백 포함) */
    static final int MIN_BODY_LENGTH = 250;
    static final int MAX_BODY_LENGTH = 600;

    /** 철학자 한 명의 소제목 + 내용 묶음 수 상한 */
    static final int MAX_SECTIONS = 5;

    private PhilosophyWritingValidator() {
    }

    public static List<PhilosopherWriting> validate(List<PhilosopherWriting> writings) {
        if (writings == null
                || writings.size() < PhilosophyWriter.MIN_PHILOSOPHERS
                || writings.size() > PhilosophyWriter.MAX_PHILOSOPHERS) {
            throw invalid("철학자 수가 " + PhilosophyWriter.MIN_PHILOSOPHERS + "~"
                    + PhilosophyWriter.MAX_PHILOSOPHERS + "명이 아닙니다: " + (writings == null ? null : writings.size()));
        }

        List<PhilosopherWriting> result = new ArrayList<>(writings.size());
        Set<String> names = new HashSet<>();
        for (PhilosopherWriting writing : writings) {
            String name = writing.philosopherName().strip();
            if (name.isEmpty() || name.length() > PhilosopherContent.PHILOSOPHER_NAME_MAX_LENGTH) {
                throw invalid("철학자 이름이 비었거나 너무 깁니다: " + name.length() + "자");
            }
            if (!names.add(name)) {
                throw invalid("같은 철학자가 두 번 나왔습니다: " + name);
            }
            result.add(new PhilosopherWriting(name, validateSections(name, writing.sections())));
        }
        return List.copyOf(result);
    }

    private static List<Section> validateSections(String name, List<Section> sections) {
        if (sections.isEmpty() || sections.size() > MAX_SECTIONS) {
            throw invalid(name + " 의 소제목 + 내용 묶음 수가 1~" + MAX_SECTIONS + "개가 아닙니다: " + sections.size());
        }
        List<Section> result = new ArrayList<>(sections.size());
        int bodyLength = 0;
        for (Section section : sections) {
            String subtitle = section.subtitle().strip();
            String body = section.body().strip();
            if (subtitle.isEmpty() || subtitle.length() > PhilosophySection.SUBTITLE_MAX_LENGTH) {
                throw invalid(name + " 의 소제목이 비었거나 너무 깁니다: " + subtitle.length() + "자");
            }
            if (body.isEmpty()) {
                throw invalid(name + " 의 내용이 비었습니다");
            }
            bodyLength += body.codePointCount(0, body.length());
            result.add(new Section(subtitle, body));
        }
        if (bodyLength < MIN_BODY_LENGTH || bodyLength > MAX_BODY_LENGTH) {
            throw invalid(name + " 의 글 길이가 " + MIN_BODY_LENGTH + "~" + MAX_BODY_LENGTH + "자가 아닙니다: " + bodyLength + "자");
        }
        return result;
    }

    private static AiException invalid(String message) {
        return new AiException("1분 철학 글 형식이 맞지 않습니다 — " + message, true);
    }
}
