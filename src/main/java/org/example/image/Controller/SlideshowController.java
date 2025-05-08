package org.example.image.Controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
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
    private Button closeButton;//添加关闭按钮
    @FXML
    private Label statusLabel;
    @FXML
    private HBox previewBox;
    @FXML
    private BorderPane mainPane;
    @FXML
    private ScrollPane imageScrollPane;
    @FXML
    private StackPane imageContainer;
    @FXML
    private HBox toolbar;




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
       setupLayout();

    }

    private void setupLayout(){
        // 设置图片容器和滚动面板
        imageScrollPane.setFitToWidth(true);
        imageScrollPane.setFitToHeight(true);
        imageContainer.setPrefSize(800,600);//设置默认大小

        // 设置工具栏样式，固定在底部中央
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setPadding(new Insets(10));
        toolbar.setSpacing(10);
        toolbar.setStyle("-fx-background-color: #f0f0f0;");

        // 添加关闭按钮
        closeButton = new Button("关闭");
        closeButton.setOnAction(event -> {
            // 获取当前窗口并关闭
            Stage stage = (Stage) mainPane.getScene().getWindow();
            stage.close();
        });
        toolbar.getChildren().add(closeButton);

        // 添加键盘事件支持
        mainPane.setOnKeyPressed(event->{
            switch (event.getCode()){
                case LEFT:handlePrev() ;break;
                case RIGHT:handleNext() ;break;
                case PLUS:
                case EQUALS:handleZoomIn();break;
                case MINUS:handleZoomOut() ;break;
                case SPACE:handlePlay();break;
                case ESCAPE: closeButton.fire(); break; // 添加ESC键关闭窗口
            }
        }) ;

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
            try{
                updatePreviewBar() ;// 调用更新图片列表的方法
            }catch (FileNotFoundException e) {
                e.printStackTrace();
            }

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
            try {
                updatePreviewBar(); // 调用更新图片列表的方法
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            statusLabel.setText("已经是最后一张图片");
        }
    }


    @FXML
    private void handleZoomIn() {
       /* if (currentScale < MAX_SCALE) {
            currentScale *= 1.2;
            if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;
            updateImageViewSize();
        }*/
        //先计算目标缩放比例 targetScale，然后根据最大缩放比例 MAX_SCALE 和最小缩放比例 MIN_SCALE 判断是否执行缩放操作。
       //每次执行缩放操作时，更新 currentScale 并调用 updateImageViewSize 方法来调整图片视图的大小
        double targetScale = currentScale * 1.2;
        if (targetScale <= MAX_SCALE) {
            currentScale = targetScale;
            updateImageViewSize();
        }
    }

    @FXML
    private void handleZoomOut() {
        /*if (currentScale > MIN_SCALE) {
            currentScale /= 1.2;
            if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
            updateImageViewSize();
        }*/
        double targetScale = currentScale / 1.2;
        if (targetScale >= MIN_SCALE) {
            currentScale = targetScale;
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
        // 计算显示的起始索引（确保不小于0）
        int startIndex = Math.max(0, currentIndex - 2);
        // 计算显示的结束索引（确保不超过图片总数）
        int endIndex = Math.min(imageFiles.size() - 1, currentIndex + 2);

        if (endIndex - startIndex < 4) {
            startIndex = Math.max(0, endIndex - 4);
        }

        final double THUMBNAIL_WIDTH = 70;
        final double THUMBNAIL_HEIGHT = 70;

        for (int i = startIndex; i <= endIndex; i++) {
            File file = imageFiles.get(i);
            ImageView thumb = new ImageView(new Image(
                    new FileInputStream(file),
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT,
                    false,
                    true
            ));

            thumb.setFitWidth(THUMBNAIL_WIDTH);
            thumb.setFitHeight(THUMBNAIL_HEIGHT);
            thumb.setPreserveRatio(false);
            thumb.setSmooth(true);

            StackPane thumbContainer = new StackPane();
            thumbContainer.getChildren().add(thumb);
            thumbContainer.getStyleClass().add("preview-thumb-container");

            if (i == currentIndex) {
                thumbContainer.getStyleClass().add("selected");
            }

            final int idx = i;
            thumbContainer.setOnMouseClicked(e -> {
                currentIndex = idx;
                try {
                    loadCurrentImage();
                    updateStatus();
                    updatePreviewBar();
                } catch (FileNotFoundException ex) {
                    // 处理文件不存在的情况
                    System.err.println("无法加载图片: " + file.getAbsolutePath());
                    System.err.println("错误信息: " + ex.getMessage());

                    // 可选：显示错误提示给用户
                    statusLabel.setText("无法加载图片: " + file.getName());
                }
            });

            previewBox.getChildren().add(thumbContainer);
        }

    }

    //缩放图片
    private void updateImageViewSize() {
        // 计算最大允许高度，防止遮挡工具栏和预览条
      //  double maxHeight = IMAGE_AREA_HEIGHT;
      //  imageView.setFitHeight(maxHeight * currentScale);
      //  imageView.setFitWidth(800 * currentScale);

        if (imageView.getImage() == null) return;

        double imageRatio = imageView.getImage().getWidth() / imageView.getImage().getHeight();
        double containerWidth = imageContainer.getWidth();
        double containerHeight = imageContainer.getHeight();

        // 计算基于容器的最佳尺寸
        double scaledWidth = containerWidth * currentScale;
        double scaledHeight = containerHeight * currentScale;

        // 保持图片比例
        if (scaledWidth / scaledHeight > imageRatio) {
            scaledWidth = scaledHeight * imageRatio;
        } else {
            scaledHeight = scaledWidth / imageRatio;
        }

      /*  // 保持图片比例
        if(scaledWidth /scaledHeight >imageRatio){
            scaledWidth =scaledHeight *imageRatio ;

        }else{
            scaledHeight =scaledWidth *imageRatio ;
        }*/

        imageView.setFitWidth(scaledWidth);
        imageView.setFitHeight(scaledHeight);
    }

}