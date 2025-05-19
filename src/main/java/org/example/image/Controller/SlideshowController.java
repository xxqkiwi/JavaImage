package org.example.image.Controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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
    private boolean initialLayoutComplete = false;


    private final double ZOOM_FACTOR = 1.2; // 缩放因子
    private final double MAX_SCALE = 5.0; // 最大放大倍数
    private final double MIN_SCALE = 0.05; // 最小缩小倍数
    private final double IMAGE_AREA_HEIGHT = 600; // 主图区域高度
    private final double TOOLBAR_HEIGHT = 60; // 工具栏高度
    private final double PREVIEW_HEIGHT = 80; // 预览条高度

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupSlideshowTimeline();
        setupLayout();
        setupInitializationListener();
        setupListeners();

    }
    private void setupInitializationListener() {
        // 监听场景和窗口初始化完成
        mainPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        // 窗口显示后延迟执行，确保所有组件已布局
                        newWindow.setOnShown(e -> Platform.runLater(() -> {
                            initialLayoutComplete = true;
                            resetScale();
                            updateImageViewSize();
                        }));
                    }
                });
            }
        });
    }


    private void setupListeners() {
        // 监听窗口尺寸变化，自动调整图片大小
        mainPane.widthProperty().addListener((obs, oldVal, newVal) -> updateImageViewSize());
        mainPane.heightProperty().addListener((obs, oldVal, newVal) -> updateImageViewSize());

        // 新增，监听工具栏和预览条尺寸变化
        toolbar.heightProperty().addListener((obs, oldVal, newVal) -> updateImageViewSize());
        previewBox.heightProperty().addListener((obs, oldVal, newVal) -> updateImageViewSize());
    }

    private void setupLayout(){
        // 设置图片容器和滚动面板
        imageScrollPane.setFitToWidth(false);
        imageScrollPane.setFitToHeight(false);

        // 设置工具栏样式，固定在底部中央
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setPadding(new Insets(10));
        toolbar.setSpacing(10);
        //toolbar.setStyle("-fx-background-color: #f0f0f0;");

        // 设置按钮焦点遍历
        setupButtonFocusTraversal();

        // 双击图片恢复原来大小
        imageView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                resetScale();
                updateImageViewSize();
            }
        });

        // 添加键盘事件支持
        mainPane.setOnKeyPressed(event->{
            switch (event.getCode()){
                case LEFT: handlePrev(); break;
                case RIGHT: handleNext(); break;
                case PLUS:
                case EQUALS: handleZoomIn(); break;
                case MINUS: handleZoomOut(); break;
                case SPACE: handleSpaceKey(); break;
                case ESCAPE: closeButton.fire(); break;
            }
        });
    }

    private void setupButtonFocusTraversal() {
        // 设置按钮的焦点遍历顺序
        zoomInButton.setFocusTraversable(true);
        zoomOutButton.setFocusTraversable(true);
        prevButton.setFocusTraversable(true);
        nextButton.setFocusTraversable(true);
        playButton.setFocusTraversable(true);
        closeButton.setFocusTraversable(true);

        // 设置初始焦点
        zoomInButton.requestFocus();
    }

    private void handleSpaceKey() {
        // 获取当前获得焦点的按钮并触发其动作
        if (zoomInButton.isFocused()) {
            handleZoomIn();
        } else if (zoomOutButton.isFocused()) {
            handleZoomOut();
        } else if (prevButton.isFocused()) {
            handlePrev();
        } else if (nextButton.isFocused()) {
            handleNext();
        } else if (playButton.isFocused()) {
            handlePlay();
        } else if (closeButton.isFocused()) {
            handleClose();
        }
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
            //重置缩放比例为初始值
            //    resetScale();
             //   updateImageViewSize();


            // 仅在初始布局完成后重置缩放
            if (initialLayoutComplete) {
                resetScale();
                updateImageViewSize();
            }
           // imageView.setFitWidth(800 * currentScale);
            //imageView.setFitHeight(600 * currentScale);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 重置缩放比例为初始适应值
    private void resetScale() {
       /* if (imageView.getImage() == null) return;

        // 计算可用空间（减去工具栏和预览条的高度）
        double availableWidth = mainPane.getWidth();
        double availableHeight = mainPane.getHeight() - toolbar.getPrefHeight() - previewBox.getPrefHeight();

        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();

        // 计算能使图片完全显示的缩放比例
        double scaleX = availableWidth / imageWidth;
        double scaleY = availableHeight / imageHeight;

        // 使用较小的缩放比例，确保图片能完整显示
        currentScale = Math.min(scaleX, scaleY);

        // 确保缩放比例在合理范围内
        currentScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale));

        */
        if (imageView.getImage() == null || !initialLayoutComplete) return;

        // 获取窗口实际尺寸
        Stage stage = (Stage) mainPane.getScene().getWindow();
        double windowWidth = stage.getWidth();
        double windowHeight = stage.getHeight();

        // 计算可用空间（扣除工具栏、预览条和边框内边距）
        double contentWidth = windowWidth - mainPane.getPadding().getLeft() - mainPane.getPadding().getRight();
        double contentHeight = windowHeight - toolbar.getHeight() - previewBox.getHeight()
                - mainPane.getPadding().getTop() - mainPane.getPadding().getBottom();

        // 确保可用空间有效
        contentWidth = Math.max(100, contentWidth);
        contentHeight = Math.max(100, contentHeight);

        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();

        // 计算宽高方向的缩放比例
        double scaleX = contentWidth / imageWidth;
        double scaleY = contentHeight / imageHeight;

        // 取较小比例确保完整显示
        currentScale = Math.min(scaleX, scaleY);

        // 限制缩放范围
        currentScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale));



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
        // 统一缩放逻辑，先计算新比例再检查边界
       // currentScale *= ZOOM_FACTOR;
        //currentScale = Math.min(currentScale, MAX_SCALE);
       // updateImageViewSize();

        currentScale = Math.min(currentScale * ZOOM_FACTOR, MAX_SCALE);
        updateImageViewSize();






       /*if (currentScale < MAX_SCALE) {
            currentScale *= 1.2;
            if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;
            updateImageViewSize();
        }*/
        //先计算目标缩放比例 targetScale，然后根据最大缩放比例 MAX_SCALE 和最小缩放比例 MIN_SCALE 判断是否执行缩放操作。
        //每次执行缩放操作时，更新 currentScale 并调用 updateImageViewSize 方法来调整图片视图的大小
      /*  double targetScale = currentScale * 1.2;
        if (targetScale <= MAX_SCALE) {
            currentScale = targetScale;
            updateImageViewSize();
        }*/
    }

    @FXML
    private void handleZoomOut() {
        // 统一缩放逻辑，先计算新比例再检查边界
        //currentScale /= ZOOM_FACTOR;
       // currentScale = Math.max(currentScale, MIN_SCALE);
       // updateImageViewSize();
        currentScale = Math.max(currentScale / ZOOM_FACTOR, MIN_SCALE);
        updateImageViewSize();




       /* if (currentScale > MIN_SCALE) {
            currentScale /= 1.2;
            if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
            updateImageViewSize();
        }*/
      /*  double targetScale = currentScale / 1.2;
        if (targetScale >= MIN_SCALE) {
            currentScale = targetScale;
            updateImageViewSize();
        }*/
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
    // 关闭按钮
    @FXML
    private void handleClose() {
        // 获取当前窗口并关闭
        Stage stage = (Stage) mainPane.getScene().getWindow();
        stage.close();
    }

    //更新缩略图区域
    private void updatePreviewBar() throws FileNotFoundException {
        previewBox.getChildren().clear();
        // 计算显示的起始索引（确保不小于0）
        int startIndex = Math.max(0, currentIndex - 2);
        // 计算显示的结束索引（确保不超过图片总数）
        int endIndex = Math.min(imageFiles.size() - 1, currentIndex + 2);

        //保证一行五个图片
        // 如果当前图片靠近开头，调整起始索引
        /*if (currentIndex < 2) {
            startIndex = 0;
            endIndex = Math.min(imageFiles.size() - 1, 4);
        }
        // 如果当前图片靠近结尾，调整结束索引
        else if (currentIndex > imageFiles.size() - 3) {
            startIndex = Math.max(0, imageFiles.size() - 5);
            endIndex = imageFiles.size() - 1;
        }*/
        if (endIndex - startIndex < 4) {
            startIndex = Math.max(0, endIndex - 4);
        }

        final double THUMBNAIL_WIDTH = 70;
        final double THUMBNAIL_HEIGHT = 70;

        for (int i = startIndex; i <= endIndex; i++) {
            File file = imageFiles.get(i);
            //ImageView thumb = new ImageView(new Image(new FileInputStream(file), 70, 70, true, true));
            //thumb.getStyleClass().add("preview-thumb");
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
                //thumb.getStyleClass().add("selected");
                thumbContainer.getStyleClass().add("selected");
            }
            final int idx = i;
            //thumb.setOnMouseClicked(e -> {
            thumbContainer.setOnMouseClicked(e -> {
                currentIndex = idx;
                //loadCurrentImage();
                //updateStatus();
                try {
                    loadCurrentImage();
                    updateStatus();
                    updatePreviewBar();
                } catch (FileNotFoundException ex) {
                    //throw new RuntimeException(ex);
                    //ex.printStackTrace();
                    // 处理文件不存在的情况
                    System.err.println("无法加载图片: " + file.getAbsolutePath());
                    System.err.println("错误信息: " + ex.getMessage());

                    // 显示错误提示给用户
                    statusLabel.setText("无法加载图片: " + file.getName());
                }
            });
            //previewBox.getChildren().add(thumb);
            previewBox.getChildren().add(thumbContainer);
        }
    }

    //缩放图片
    private void updateImageViewSize() {
      /*  if (imageView.getImage() == null) return;

        // 获取图片和容器信息
        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();
        double imageRatio = imageWidth / imageHeight;

        // 计算可用空间（减去工具栏和预览条的高度）
        double availableWidth = mainPane.getWidth();
        double availableHeight = mainPane.getHeight() - toolbar.getPrefHeight() - previewBox.getPrefHeight();

        // 计算基于当前缩放比例的目标尺寸
        double targetWidth = availableWidth * currentScale;
        double targetHeight = availableHeight * currentScale;

        // 保持图片比例
        if (targetWidth / targetHeight > imageRatio) {
            targetWidth = targetHeight * imageRatio;
        } else {
            targetHeight = targetWidth / imageRatio;
        }

        // 应用尺寸并居中显示
        imageView.setFitWidth(targetWidth);
        imageView.setFitHeight(targetHeight);
        imageContainer.setAlignment(Pos.CENTER);

       */

        if (imageView.getImage() == null || !initialLayoutComplete) return;

        // 获取图片原始尺寸
        double imageWidth = imageView.getImage().getWidth();
        double imageHeight = imageView.getImage().getHeight();

        // 计算缩放后的尺寸
        double scaledWidth = imageWidth * currentScale;
        double scaledHeight = imageHeight * currentScale;

        // 应用缩放后的尺寸
        imageView.setFitWidth(scaledWidth);
        imageView.setFitHeight(scaledHeight);

        // 居中显示
        imageContainer.setAlignment(Pos.CENTER);


    }

}