package dev.eministar.modules.tempvoice;

public class TempVoiceSettings {
    private boolean privateMode;
    private int maxMembers;
    private int bitrateKbps;
    private String description;
    private String panelMessageId;

    public TempVoiceSettings(int defaultMaxMembers, int defaultBitrateKbps) {
        this.privateMode = false;
        this.maxMembers = defaultMaxMembers;
        this.bitrateKbps = defaultBitrateKbps;
        this.description = "";
        this.panelMessageId = null;
    }

    public boolean isPrivateMode() {
        return privateMode;
    }

    public void setPrivateMode(boolean privateMode) {
        this.privateMode = privateMode;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = Math.max(1, Math.min(99, maxMembers));
    }

    public int getBitrateKbps() {
        return bitrateKbps;
    }

    public void setBitrateKbps(int bitrateKbps) {
        this.bitrateKbps = Math.max(8, bitrateKbps);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getPanelMessageId() {
        return panelMessageId;
    }

    public void setPanelMessageId(String panelMessageId) {
        this.panelMessageId = panelMessageId;
    }
}

