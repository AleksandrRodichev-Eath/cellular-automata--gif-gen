package com.cellmachine.telegram.bot;

import com.cellmachine.generator.Palette2D;
import com.cellmachine.generator.SimulationOptions;
import java.time.Instant;
import java.util.Arrays;

public class ChatSession {

    private final long chatId;
    private ConversationStep step = ConversationStep.CHOOSING_MODE;
    private Instant lastInteraction = Instant.now();

    private String birthDigits;
    private String survivalDigits;
    private Integer width;
    private Integer height;
    private Integer steps;
    private Double density;
    private boolean[] mask;
    private Boolean wrap;
    private Palette2D palette;
    private Integer lastPromptMessageId;
    private Integer loadingMessageId;
    private boolean simpleRuleMode;
    private SimulationOptions presetOptions;

    public ChatSession(long chatId) {
        this.chatId = chatId;
    }

    public long chatId() {
        return chatId;
    }

    public ConversationStep step() {
        return step;
    }

    public void step(ConversationStep step) {
        this.step = step;
        markInteraction();
    }

    public Instant lastInteraction() {
        return lastInteraction;
    }

    public void markInteraction() {
        this.lastInteraction = Instant.now();
    }

    public String birthDigits() {
        return birthDigits;
    }

    public void birthDigits(String birthDigits) {
        this.birthDigits = birthDigits;
        markInteraction();
    }

    public String survivalDigits() {
        return survivalDigits;
    }

    public void survivalDigits(String survivalDigits) {
        this.survivalDigits = survivalDigits;
        markInteraction();
    }

    public Integer width() {
        return width;
    }

    public Integer height() {
        return height;
    }

    public void dimensions(int width, int height) {
        this.width = width;
        this.height = height;
        markInteraction();
    }

    public Integer steps() {
        return steps;
    }

    public void steps(int steps) {
        this.steps = steps;
        markInteraction();
    }

    public Double density() {
        return density;
    }

    public void density(Double density) {
        this.density = density;
        markInteraction();
    }

    public boolean[] mask() {
        return mask == null ? null : mask.clone();
    }

    public void mask(boolean[] mask) {
        this.mask = mask == null ? null : mask.clone();
        markInteraction();
    }

    public Boolean wrap() {
        return wrap;
    }

    public void wrap(boolean wrap) {
        this.wrap = wrap;
        markInteraction();
    }

    public Palette2D palette() {
        return palette;
    }

    public void palette(Palette2D palette) {
        this.palette = palette;
        markInteraction();
    }

    public Integer lastPromptMessageId() {
        return lastPromptMessageId;
    }

    public void lastPromptMessageId(Integer lastPromptMessageId) {
        this.lastPromptMessageId = lastPromptMessageId;
        markInteraction();
    }

    public void clearLastPromptMessage() {
        this.lastPromptMessageId = null;
        markInteraction();
    }

    public Integer loadingMessageId() {
        return loadingMessageId;
    }

    public void loadingMessageId(Integer loadingMessageId) {
        this.loadingMessageId = loadingMessageId;
        markInteraction();
    }

    public boolean isReadyForGeneration() {
        if (presetOptions != null) {
            return true;
        }
        return birthDigits != null
                && survivalDigits != null
                && width != null
                && height != null
                && steps != null
                && density != null
                && wrap != null
                && palette != null;
    }

    public ChatSession snapshot() {
        ChatSession copy = new ChatSession(chatId);
        copy.step = this.step;
        copy.lastInteraction = this.lastInteraction;
        copy.birthDigits = this.birthDigits;
        copy.survivalDigits = this.survivalDigits;
        copy.width = this.width;
        copy.height = this.height;
        copy.steps = this.steps;
        copy.density = this.density;
        copy.mask = this.mask == null ? null : this.mask.clone();
        copy.wrap = this.wrap;
        copy.palette = this.palette;
        copy.lastPromptMessageId = this.lastPromptMessageId;
        copy.loadingMessageId = this.loadingMessageId;
        copy.presetOptions = this.presetOptions;
        copy.simpleRuleMode = this.simpleRuleMode;
        return copy;
    }

    public SimulationOptions presetOptions() {
        return presetOptions;
    }

    public void presetOptions(SimulationOptions presetOptions) {
        this.presetOptions = presetOptions;
        markInteraction();
    }

    public void clearPresetOptions() {
        this.presetOptions = null;
        markInteraction();
    }

    public boolean simpleRuleMode() {
        return simpleRuleMode;
    }

    public void simpleRuleMode(boolean simpleRuleMode) {
        this.simpleRuleMode = simpleRuleMode;
        markInteraction();
    }

    public void resetConfiguration() {
        this.birthDigits = null;
        this.survivalDigits = null;
        this.width = null;
        this.height = null;
        this.steps = null;
        this.density = null;
        this.mask = null;
        this.wrap = null;
        this.palette = null;
        this.presetOptions = null;
        this.lastPromptMessageId = null;
        this.loadingMessageId = null;
        this.simpleRuleMode = false;
        markInteraction();
    }

    @Override
    public String toString() {
        return "ChatSession{" +
                "chatId=" + chatId +
                ", step=" + step +
                ", lastInteraction=" + lastInteraction +
                ", birthDigits='" + birthDigits + '\'' +
                ", survivalDigits='" + survivalDigits + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", steps=" + steps +
                ", density=" + density +
                ", mask=" + (mask == null ? null : Arrays.toString(mask)) +
                ", wrap=" + wrap +
                ", palette=" + palette +
                ", lastPromptMessageId=" + lastPromptMessageId +
                ", loadingMessageId=" + loadingMessageId +
                '}';
    }
}
