package org.example.image.Controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

public class FileController implements Initializable {
    @FXML
    private TreeView<String> directoryTree;
    @FXML
    private FlowPane thumbnailPane;
    @FXML
    private Label infoLabel;
    @FXML
    private Label selectedDirLabel;
    @FXML
    private Button slideshowButton;
    @FXML
    private Label statusMessageLabel;
    @FXML
    private Label statusMessageLabelText;
    @FXML
    private Slider zoomSlider;
    @FXML
    private HBox previewBox;
    private final ObservableList<File> selectedFiles = FXCollections.observableArrayList();

    private final StringProperty statusMessage = new SimpleStringProperty();
    private final Pattern imagePattern = Pattern.compile("(?i).*\\.(jpg|jpeg|gif|png|bmp)");
    private File currentDirectory;
    private List<File> clipboardFiles = new ArrayList<>();


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupDirectoryTree();
        setupThumbnailPane();
        setupContextMenu();
        setupStatusBinding();
        setupZoomSlider();
    }

    private void setupDirectoryTree() {
        System.out.println("Setting up directory tree...");
        TreeItem<String> root = new TreeItem<>("我的电脑");
        root.setExpanded(true);

        File[] roots = File.listRoots();
        System.out.println("Root directories: " + Arrays.toString(roots));

        for (File rootDir : roots) {
            TreeItem<String> dirItem = new TreeItem<>(rootDir.getAbsolutePath());
            dirItem.setExpanded(false);
            dirItem.getChildren().add(new TreeItem<>("Loading...")); // 添加占位符
            //懒加载，添加监听，监听到用户点击目录时才会加载下一级目录
            dirItem.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                if (isNowExpanded) {
                    loadDirectoryTree(dirItem, rootDir); // 递归加载子目录
                }
            });
            root.getChildren().add(dirItem);
        }

        directoryTree.setRoot(root);
        directoryTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                currentDirectory = new File(newValue.getValue());
                loadThumbnails(currentDirectory);
            }
        });
    }

    private void loadDirectoryTree(TreeItem<String> parentItem, File directory) {
        parentItem.getChildren().clear(); // 清除占位符
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    TreeItem<String> dirItem = new TreeItem<>(file.getAbsolutePath());
                    dirItem.setExpanded(false);
                    dirItem.getChildren().add(new TreeItem<>("Loading...")); // 添加占位符
                    TreeItem<String> finalDirItem = dirItem;
                    dirItem.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                        if (isNowExpanded) {
                            loadDirectoryTree(finalDirItem, file); // 递归加载子目录
                        }
                    });
                    //dirItem.setUserData(file.getAbsolutePath()); // 存储完整路径
                    parentItem.getChildren().add(dirItem);
                }
            }
        }
    }

    private javafx.scene.shape.Rectangle selectionRectangle;
    private double dragStartX, dragStartY;

    private void setupThumbnailPane() {
        thumbnailPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown() && event.getTarget() == thumbnailPane) {
                clearSelection();
                if (!event.isControlDown()) {
                    dragStartX = event.getX();
                    dragStartY = event.getY();
                    if (selectionRectangle == null) {
                        selectionRectangle = new javafx.scene.shape.Rectangle();
                        selectionRectangle.setStroke(javafx.scene.paint.Color.BLUE);
                        selectionRectangle.setFill(javafx.scene.paint.Color.LIGHTBLUE.deriveColor(1, 1, 1, 0.3));
                        thumbnailPane.getChildren().add(selectionRectangle);
                    }
                }
            }
        });

        thumbnailPane.setOnMouseDragged(event -> {
            if (selectionRectangle != null) {
                double x = Math.min(dragStartX, event.getX());
                double y = Math.min(dragStartY, event.getY());
                double width = Math.abs(event.getX() - dragStartX);
                double height = Math.abs(event.getY() - dragStartY);
                selectionRectangle.setX(x);
                selectionRectangle.setY(y);
                selectionRectangle.setWidth(width);
                selectionRectangle.setHeight(height);

                for (javafx.scene.Node node : thumbnailPane.getChildren()) {
                    if (node instanceof VBox) {
                        VBox thumbnailBox = (VBox) node;
                        javafx.geometry.Bounds bounds = thumbnailBox.localToParent(thumbnailBox.getBoundsInLocal());
                        if (selectionRectangle.getBoundsInLocal().intersects(bounds)) {
                            File file = (File) thumbnailBox.getUserData();
                            selectImage(thumbnailBox, file);
                        }
                    }
                }
            }
        });

        thumbnailPane.setOnMouseReleased(event -> {
            if (selectionRectangle != null) {
                thumbnailPane.getChildren().remove(selectionRectangle);
                selectionRectangle = null;
            }
        });
    }

    private VBox createThumbnail(File file) {
        try {
            Image image = new Image(new FileInputStream(file), 150, 150, true, true);
            ImageView imageView = new ImageView(image);
            imageView.getStyleClass().add("thumbnail-image");
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setCache(true);

            Label label = new Label(file.getName());
            label.getStyleClass().add("thumbnail-label");
            label.setWrapText(true);
            label.setMaxWidth(150);
            label.setAlignment(Pos.CENTER);

            VBox vbox = new VBox();
            vbox.getStyleClass().add("thumbnail-container");
            vbox.setAlignment(Pos.CENTER);
            vbox.setSpacing(5);
            vbox.setPadding(new Insets(5));
            vbox.setUserData(file);

            // 创建一个容器来保持图片的宽高比
            StackPane imageContainer = new StackPane();
            imageContainer.setMaxSize(150, 150);
            imageContainer.getChildren().add(imageView);
            StackPane.setAlignment(imageView, Pos.CENTER);

            vbox.getChildren().addAll(imageContainer, label);
            VBox.setVgrow(imageContainer, Priority.ALWAYS);

            vbox.setOnMouseClicked(event -> {
                if (event.isControlDown()) {
                    toggleSelection(vbox, file);
                } else if (event.getClickCount() == 2) {
                    openSlideshow(file);
                } else {
                    clearSelection();
                    selectImage(vbox, file);
                }
            });

            return vbox;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private VBox getvBox(File file, ImageView imageView) {
        Label label = new Label(file.getName());
        VBox vbox = new VBox(imageView, label);
        vbox.setUserData(file);

        vbox.setOnMouseClicked(event -> {
            if (event.isControlDown()) {
                toggleSelection(vbox, file);
            } else if (event.getClickCount() == 2) {
                openSlideshow(file);
            } else {
                clearSelection();
                selectImage(vbox, file);
            }
        });
        return vbox;
    }

    private void openSlideshow(File startFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/image/Slideshow/slideshow-view.fxml"));
            javafx.scene.Parent root = loader.load();
            SlideshowController controller = loader.getController();

            File[] files = currentDirectory.listFiles((dir, name) -> imagePattern.matcher(name).matches());
            List<File> imageFiles = Arrays.asList(files);
            int startIndex = imageFiles.indexOf(startFile);

            controller.setImageFiles(imageFiles, startIndex);

            javafx.stage.Stage slideshowStage = new javafx.stage.Stage();
            slideshowStage.setTitle("幻灯片播放");
            slideshowStage.setScene(new javafx.scene.Scene(root));
            slideshowStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("删除");
        deleteItem.setOnAction(this::handleDelete);

        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(this::handleCopy);

        MenuItem renameItem = new MenuItem("重命名");
        renameItem.setOnAction(this::handleRename);

        contextMenu.getItems().addAll(deleteItem, copyItem, renameItem);
        thumbnailPane.setOnContextMenuRequested(event -> {
            if (!selectedFiles.isEmpty()) {
                contextMenu.show(thumbnailPane, event.getScreenX(), event.getScreenY());
            }
        });
    }

    private void setupStatusBinding() {
        infoLabel.textProperty().bind(statusMessage);
    }

    private void setupZoomSlider() {
        zoomSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double scale = newVal.doubleValue();
            double baseWidth = 200.0; // 基础宽度
            double baseHeight = 240.0; // 基础高度
            double baseImageSize = 160.0; // 基础图片大小
            double baseFontSize = 12.0; // 基础字体大小

            // 计算新的尺寸
            double newWidth = baseWidth * scale;
            double newHeight = baseHeight * scale;
            double newImageSize = baseImageSize * scale;
            double newFontSize = baseFontSize * scale;

            // 更新所有缩略图的样式
            for (javafx.scene.Node node : thumbnailPane.getChildren()) {
                if (node instanceof VBox) {
                    VBox vbox = (VBox) node;
                    vbox.setStyle(String.format(
                        "-fx-min-width: %.0fpx; -fx-max-width: %.0fpx; " +
                        "-fx-min-height: %.0fpx; -fx-max-height: %.0fpx;",
                        newWidth, newWidth, newHeight, newHeight
                    ));

                    // 更新图片和文字大小
                    for (javafx.scene.Node child : vbox.getChildren()) {
                        if (child instanceof StackPane) {
                            // 更新图片容器和图片大小
                            StackPane imageContainer = (StackPane) child;
                            imageContainer.setMaxSize(newImageSize, newImageSize);
                            for (javafx.scene.Node imageNode : imageContainer.getChildren()) {
                                if (imageNode instanceof ImageView) {
                                    ImageView imageView = (ImageView) imageNode;
                                    imageView.setFitWidth(newImageSize);
                                    imageView.setFitHeight(newImageSize);
                                }
                            }
                        } else if (child instanceof Label) {
                            // 更新标签大小和字体
                            Label label = (Label) child;
                            label.setStyle(String.format(
                                "-fx-font-size: %.1fpx; " +
                                "-fx-min-height: %.0fpx; " +
                                "-fx-pref-height: %.0fpx; " +
                                "-fx-max-width: %.0fpx;",
                                newFontSize,
                                newHeight * 0.2, // 文字区域高度为容器高度的20%
                                newHeight * 0.2,
                                newImageSize
                            ));
                        }
                    }
                }
            }
        });
    }

    private void loadThumbnails(File directory) {
        thumbnailPane.getChildren().clear();
        selectedFiles.clear();

        if (directory.isDirectory()) {
            selectedDirLabel.setText("当前目录: " + directory.getAbsolutePath());
            File[] files = directory.listFiles((dir, name) -> imagePattern.matcher(name).matches());

            if (files != null) {
                for (File file : files) {
                    VBox thumbnail = createThumbnail(file);
                    thumbnailPane.getChildren().add(thumbnail);
                }
                updateStatusMessage();
            }
        }
    }

    private void toggleSelection(VBox vbox, File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
            vbox.getStyleClass().remove("selected");
        } else {
            selectedFiles.add(file);
            vbox.getStyleClass().add("selected");
        }
        updateStatusMessage();
    }

    private void selectImage(VBox vbox, File file) {
        selectedFiles.add(file);
        vbox.getStyleClass().add("selected");
        updateStatusMessage();
    }

    private void clearSelection() {
        selectedFiles.clear();
        for (javafx.scene.Node node : thumbnailPane.getChildren()) {
            if (node instanceof VBox) {
                ((VBox) node).getStyleClass().remove("selected");
            }
        }
        updateStatusMessage();
    }

    private void updateStatusMessage() {
        statusMessageLabel.setText(String.format("找到 %d 张图片", thumbnailPane.getChildren().size()));
        statusMessageLabelText.setText(String.format("选中 %d 张图片", selectedFiles.size()));
    }

    private long calculateTotalSize(File[] files) {
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        return totalSize / 1024;
    }

    @FXML
    private void handleDelete(ActionEvent event) {
        if (!selectedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText("确定要删除选中的图片吗？");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                for (File file : selectedFiles) {
                    try {
                        Files.delete(file.toPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                loadThumbnails(currentDirectory);
            }
        }
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        clipboardFiles = new ArrayList<>(selectedFiles);
    }

    @FXML
    private void handlePaste(ActionEvent event) {
        if (!clipboardFiles.isEmpty() && currentDirectory != null) {
            for (File source : clipboardFiles) {
                try {
                    Path sourcePath = source.toPath();
                    String fileName = source.getName();
                    Path targetPath = currentDirectory.toPath().resolve(fileName);

                    if (Files.exists(targetPath)) {
                        fileName = getUniqueFileName(targetPath);
                        targetPath = currentDirectory.toPath().resolve(fileName);
                    }

                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            loadThumbnails(currentDirectory);
        }
    }

    private String getUniqueFileName(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        String name = fileName.substring(0, dotIndex);
        String ext = fileName.substring(dotIndex);

        int counter = 1;
        while (Files.exists(path)) {
            fileName = name + "_" + counter + ext;
            path = path.getParent().resolve(fileName);
            counter++;
        }
        return fileName;
    }

    @FXML
    private void handleRename(ActionEvent event) {
        if (!selectedFiles.isEmpty()) {
            if (selectedFiles.size() == 1) {
                TextInputDialog dialog = new TextInputDialog(selectedFiles.get(0).getName());
                dialog.setTitle("重命名文件");
                dialog.setHeaderText("输入新的文件名");
                dialog.setContentText("文件名:");

                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    renameFile(selectedFiles.get(0), result.get());
                }
            } else {
                // 批量重命名
                TextInputDialog prefixDialog = new TextInputDialog("NewName");
                prefixDialog.setTitle("批量重命名");
                prefixDialog.setHeaderText("输入名称前缀");
                prefixDialog.setContentText("前缀:");

                Optional<String> prefixResult = prefixDialog.showAndWait();
                if (prefixResult.isPresent()) {
                    String prefix = prefixResult.get();

                    TextInputDialog startIndexDialog = new TextInputDialog("1");
                    startIndexDialog.setTitle("批量重命名");
                    startIndexDialog.setHeaderText("输入起始编号");
                    startIndexDialog.setContentText("起始编号:");

                    Optional<String> startIndexResult = startIndexDialog.showAndWait();
                    if (startIndexResult.isPresent()) {
                        int startIndex = Integer.parseInt(startIndexResult.get());

                        TextInputDialog digitCountDialog = new TextInputDialog("4");
                        digitCountDialog.setTitle("批量重命名");
                        digitCountDialog.setHeaderText("输入编号位数");
                        digitCountDialog.setContentText("编号位数:");

                        Optional<String> digitCountResult = digitCountDialog.showAndWait();
                        if (digitCountResult.isPresent()) {
                            int digitCount = Integer.parseInt(digitCountResult.get());

                            int index = startIndex;
                            for (File file : selectedFiles) {
                                String newName = prefix + String.format("%0" + digitCount + "d", index) + getFileExtension(file);
                                renameFile(file, newName);
                                index++;
                            }
                        }
                    }
                }
            }
            loadThumbnails(currentDirectory);
        }
    }

    private void renameFile(File file, String newName) {
        String ext = getFileExtension(file);
        File newFile = new File(file.getParent(), newName);
        if (file.renameTo(newFile)) {
            System.out.println("文件重命名成功: " + newFile.getName());
        } else {
            System.out.println("文件重命名失败: " + file.getName());
        }
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastIndex = name.lastIndexOf('.');
        return lastIndex != -1 ? name.substring(lastIndex) : "";
    }

    @FXML
    private void openSlideshow(ActionEvent event) {
        if (currentDirectory != null) {
            File[] files = currentDirectory.listFiles((dir, name) -> imagePattern.matcher(name).matches());
            if (files != null && files.length > 0) {
                openSlideshow(files[0]);
            }
        }
    }
}