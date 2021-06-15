package net.adoptium.model;

public class DownloadErrorTemplate {

    private final String errorMessage;

    private final String suggestion;

    public DownloadErrorTemplate(final String errorMessage, final String suggestion) {
        this.errorMessage = errorMessage;
        this.suggestion = suggestion;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSuggestion() {
        return suggestion;
    }
}
