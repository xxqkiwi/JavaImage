package org.example.image.Controller;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class SlideshowController implements Initializable {
    @FXML
    private ImageView imageView;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button zoomInButton;
    @FXML
    private Button zoomOutButton;
    @FXML
    private Button playButton;

    private List<File> imageFiles;
    private int currentIndex;
    private double scale = 1.0;
    private AnimationTimer slideshowTimer;
    private boolean isPlaying = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupButtons();
    }

    public void setImageFiles(List<File> files, int startIndex) {
        this.imageFiles = new ArrayList<>(files);
        this.currentIndex = startIndex;
        loadImage();
    }

    private void setupButtons() {
        prevButton.setOnAction(event -> showPreviousImage());
        nextButton.setOnAction(event -> showNextImage());
        zoomInButton.setOnAction(event -> zoomIn());
        zoomOutButton.setOnAction(event -> zoomOut());
        playButton.setOnAction(event -> togglePlay());
    }

    private void loadImage() {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            try {
                File file = imageFiles.get(currentIndex);
                Image image = new Image(new FileInputStream(file));
                imageView.setImage(image);
                imageView.setFitWidth(image.getWidth() * scale);
                imageView.setFitHeight(image.getHeight() * scale);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private void showPreviousImage() {
        if (currentIndex > 0) {
            currentIndex--;
            loadImage();
        } else {
            showAlert("已经是第一张图片了。");
        }
    }

    private void showNextImage() {
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            loadImage();
        } else {
            showAlert("已经是最后一张图片了。");
        }
    }

    private void zoomIn() {
        scale *= 1.1;
        imageView.setFitWidth(imageView.getImage().getWidth() * scale);
        imageView.setFitHeight(imageView.getImage().getHeight() * scale);
    }

    private void zoomOut() {
        if (scale > 0.1) {
            scale *= 0.9;
            imageView.setFitWidth(imageView.getImage().getWidth() * scale);
            imageView.setFitHeight(imageView.getImage().getHeight() * scale);
        }
    }

    private void togglePlay() {
        if (isPlaying) {
            stopSlideshow();
        } else {
            startSlideshow();
        }
    }

    private void startSlideshow() {
        isPlaying = true;
        playButton.setText("停止");
        slideshowTimer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 1_000_000_000) {
                    showNextImage();
                    lastUpdate = now;
                }
            }
        };
        slideshowTimer.start();
    }

    private void stopSlideshow() {
        isPlaying = false;
        playButton.setText("播放");
        if (slideshowTimer != null) {
            slideshowTimer.stop();
        }
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("提示");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void closeWindow(MouseEvent event) {
        Stage stage = (Stage) ((javafx.scene.Node) event.getSource()).getScene().getWindow();
        stage.close();
    }
}