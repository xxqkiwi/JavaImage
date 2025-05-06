package org.example.image.Controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
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
    @FXML
    private Label statusLabel;
    @FXML
    private HBox previewBox;

    private List<File> imageFiles; //存储图片列表
    private int currentIndex;
    private double currentScale = 1.0; //缩放比例
    private Timeline slideshowTimeline; //自动播放的时间
    private boolean isPlaying = false; //是否自动播放

    private final double MAX_SCALE = 2.5; // 最大放大倍数
    private final double MIN_SCALE = 0.2; // 最小缩小倍数
    private final double IMAGE_AREA_HEIGHT = 600; // 主图区域高度
    private final double TOOLBAR_HEIGHT = 60; // 工具栏高度
    private final double PREVIEW_HEIGHT = 80; // 预览条高度

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupSlideshowTimeline();
    }

    //当前文件夹下的图片集合
    public void setImageFiles(List<File> files, int startIndex) throws FileNotFoundException {
        this.imageFiles = files;
        this.currentIndex = startIndex;
        loadCurrentImage();
        updateStatus();
        updatePreviewBar();
    }

    //自动播放
    private void setupSlideshowTimeline() {
        //每隔一秒触发一次
        slideshowTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), event -> handleNext())
        );
        slideshowTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    private void loadCurrentImage() {
        try {
            File currentFile = imageFiles.get(currentIndex);
            Image image = new Image(new FileInputStream(currentFile));
            imageView.setImage(image);
            imageView.setFitWidth(800 * currentScale);
            imageView.setFitHeight(600 * currentScale);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updateStatus() {
        statusLabel.setText(String.format("图片 %d / %d", currentIndex + 1, imageFiles.size()));
    }

    @FXML
    private void handlePrev() {
        if (currentIndex > 0) {
            currentIndex--;
            loadCurrentImage();
            updateStatus();
        } else {
            statusLabel.setText("已经是第一张图片");
        }
    }

    @FXML
    private void handleNext() {
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            loadCurrentImage();
            updateStatus();
        } else {
            statusLabel.setText("已经是最后一张图片");
        }
    }


    @FXML
    private void handleZoomIn() {
        if (currentScale < MAX_SCALE) {
            currentScale *= 1.2;
            if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;
            updateImageViewSize();
        }
    }

    @FXML
    private void handleZoomOut() {
        if (currentScale > MIN_SCALE) {
            currentScale /= 1.2;
            if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
            updateImageViewSize();
        }
    }

    @FXML
    private void handlePlay() {
        if (isPlaying) {
            slideshowTimeline.stop();
            playButton.setText("播放");
            isPlaying = false;
        } else {
            slideshowTimeline.play();
            playButton.setText("停止");
            isPlaying = true;
        }
    }
    private void updatePreviewBar() throws FileNotFoundException {
        previewBox.getChildren().clear();
        for (int i = 0; i < imageFiles.size(); i++) {
            File file = imageFiles.get(i);
            ImageView thumb = new ImageView(new Image(new FileInputStream(file), 70, 70, true, true));
            thumb.getStyleClass().add("preview-thumb");
            if (i == currentIndex) {
                thumb.getStyleClass().add("selected");
            }
            final int idx = i;
            thumb.setOnMouseClicked(e -> {
                currentIndex = idx;
                loadCurrentImage();
                updateStatus();
                try {
                    updatePreviewBar();
                } catch (FileNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            });
            previewBox.getChildren().add(thumb);
        }
    }

    //缩放图片
    private void updateImageViewSize() {
        // 计算最大允许高度，防止遮挡工具栏和预览条
        double maxHeight = IMAGE_AREA_HEIGHT;
        imageView.setFitHeight(maxHeight * currentScale);
        imageView.setFitWidth(800 * currentScale);
    }

}