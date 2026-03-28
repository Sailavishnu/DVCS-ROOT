package com.dvcs.client.workspacepage.controller;

import com.dvcs.client.workspacepage.model.FileItemModel;
import com.dvcs.client.workspacepage.utils.DateTimeUtils;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

public final class FileController {

    public void renderFileHeader(
            FileItemModel file,
            Label fileNameLabel,
            Label lastCommitMessageLabel,
            Label lastCommitTimeLabel) {
        if (file == null) {
            fileNameLabel.setText("No file selected");
            lastCommitMessageLabel.setText("No commit metadata");
            lastCommitTimeLabel.setText("No commits yet");
            return;
        }

        fileNameLabel.setText(file.filename());
        lastCommitMessageLabel.setText(file.latestCommitMessage());
        lastCommitTimeLabel.setText(DateTimeUtils.formatInstant(file.latestCommitAt()));
    }

    public void setEditorMode(TextArea editor, boolean editing) {
        editor.setEditable(editing);
        editor.setDisable(false);
    }
}
