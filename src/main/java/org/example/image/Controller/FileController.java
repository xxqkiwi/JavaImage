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
import javafx.scene.input.Clipboard;
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
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;


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
    private Label message;
    @FXML
    private Slider zoomSlider;
    @FXML
    private HBox previewBox;
    @FXML
    private TextField searchField;
    @FXML
    private TextField pathField;
    @FXML
    private Button backButton;
    @FXML
    private Button forwardButton;
    @FXML
    private Button upButton;

    private final ObservableList<File> selectedFiles = FXCollections.observableArrayList();
    private final StringProperty statusMessage = new SimpleStringProperty();
    private final Pattern imagePattern = Pattern.compile("(?i).*\\.(jpg|jpeg|gif|png|bmp)");
    private File currentDirectory;
    private List<File> clipboardFiles = new ArrayList<>();

    private double dragStartX, dragStartY;
    private Stack<File> navigationHistory = new Stack<>();
    private Stack<File> forwardHistory = new Stack<>();

    private ContextMenu contextMenu;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupDirectoryTree();
        setupThumbnailPane();
        setupContextMenu();
        setupStatusBinding();
        setupZoomSlider();
        setupSearchField();
        setupPathField();
        setupNavigationButtons();
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
                // 获取选中项的完整路径
                String fullPath = getFullPath(newValue);
                File newDir = new File(fullPath);
                if (newDir.isDirectory()) {
                    if (currentDirectory != null) {
                        navigationHistory.push(currentDirectory);
                        forwardHistory.clear();
                    }
                    currentDirectory = newDir;
                    loadThumbnails(currentDirectory);
                    updateNavigationButtons();
                }
            }
        });
    }

    private String getFullPath(TreeItem<String> item) {
        StringBuilder path = new StringBuilder();
        TreeItem<String> current = item;

        while (current != null && current != directoryTree.getRoot()) {
            if (path.length() > 0) {
                path.insert(0, File.separator);
            }
            path.insert(0, current.getValue());
            current = current.getParent();
        }

        return path.toString();
    }

    private void loadDirectoryTree(TreeItem<String> parentItem, File directory) {
        parentItem.getChildren().clear(); // 清除占位符
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 只显示文件夹名称
                    TreeItem<String> dirItem = new TreeItem<>(file.getName());
                    dirItem.setExpanded(false);
                    dirItem.getChildren().add(new TreeItem<>("Loading...")); // 添加占位符
                    TreeItem<String> finalDirItem = dirItem;
                    dirItem.expandedProperty().addListener((obs, wasExpanded, isNowExpanded) -> {
                        if (isNowExpanded) {
                            loadDirectoryTree(finalDirItem, file); // 递归加载子目录
                        }
                    });
                    parentItem.getChildren().add(dirItem);
                }
            }
        }
    }

    private void setupSearchField() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (currentDirectory != null) {
                filterThumbnails(newValue);
            }
        });
    }

    private void setupPathField() {
        pathField.setOnAction(event -> {
            String newPath = pathField.getText().trim();
            File newDir = new File(newPath);
            if (newDir.exists() && newDir.isDirectory()) {
                currentDirectory = newDir;
                loadThumbnails(currentDirectory);
                updateDirectoryTreeSelection(newDir);
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("路径错误");
                alert.setHeaderText("无效的目录路径");
                alert.setContentText("请检查输入的路径是否正确。");
                alert.showAndWait();
            }
        });
    }

    private void updateDirectoryTreeSelection(File directory) {
        String path = directory.getAbsolutePath();
        TreeItem<String> root = directoryTree.getRoot();
        for (TreeItem<String> item : root.getChildren()) {
            if (item.getValue().equals(path)) {
                directoryTree.getSelectionModel().select(item);
                break;
            }
        }
    }

    private void filterThumbnails(String searchText) {
        thumbnailPane.getChildren().clear();
        selectedFiles.clear();

        if (currentDirectory != null) {
            File[] files = currentDirectory.listFiles((dir, name) -> {
                boolean isImage = imagePattern.matcher(name).matches();
                boolean matchesSearch = searchText.isEmpty() ||
                    name.toLowerCase().contains(searchText.toLowerCase());
                return isImage && matchesSearch;
            });

            if (files != null) {
                for (File file : files) {
                    VBox thumbnail = createThumbnail(file);
                    thumbnailPane.getChildren().add(thumbnail);
                }
            }
            updateStatusMessage();
        }
    }
    private boolean isMultiSelecting = false;// 是否处于多选状态

    private void setupThumbnailPane() {
        thumbnailPane.setOnMousePressed(event -> {
            if (event.isPrimaryButtonDown() && event.getTarget() == thumbnailPane) {
                if (!event.isControlDown()) {
                    clearSelection();
                }
                dragStartX = event.getX();
                dragStartY = event.getY();
            }
        });

        thumbnailPane.setOnMouseDragged(event -> {
            for (javafx.scene.Node node : thumbnailPane.getChildren()) {
                if (node instanceof VBox) {
                    VBox thumbnailBox = (VBox) node;
                    javafx.geometry.Bounds bounds = thumbnailBox.localToParent(thumbnailBox.getBoundsInLocal());
                    if (bounds.contains(event.getX(), event.getY())) {
                        File file = (File) thumbnailBox.getUserData();
                        if (!selectedFiles.contains(file)) {
                            selectImage(thumbnailBox, file);
                        }
                    }
                }
                event.consume();
            }
        });

        thumbnailPane.setOnMouseClicked(event -> {
            if (event.getTarget() == thumbnailPane) {
                clearSelection();
            }
        });
    }

    private VBox createThumbnail(File file) {
        try(FileInputStream fis = new FileInputStream(file)) {
            Image image = new Image(fis, 150, 150, true, true);
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

            /*vbox.setOnMouseClicked(event -> {
                if (event.isControlDown()) {
                    toggleSelection(vbox, file);
                } else if (event.getClickCount() == 2) {
                    openSlideshow(file);
                } else {
                    clearSelection();
                    selectImage(vbox, file);
                }
            });*/

            vbox.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                    // 如果点击的是未选中项，自动选中
                    if (!selectedFiles.contains(file)) {
                        clearSelection();
                        selectImage(vbox, file);
                    }
                    event.consume();
                } else {
                    // 原有左键处理逻辑
                    if (event.isControlDown()) {
                        toggleSelection(vbox, file);
                    } else if (event.getClickCount() == 2) {
                        openSlideshow(file);
                    } else {
                        clearSelection();
                        selectImage(vbox, file);
                    }
                }
            });
            return vbox;
        } catch (IOException e) {
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

    private boolean isCutOperation = false; // true表示剪切，false表示复制

    private void setupContextMenu() {
        contextMenu = new ContextMenu();

        MenuItem copyItem = new MenuItem("复制");
        copyItem.setOnAction(this::handleCopy);

        MenuItem cutItem = new MenuItem("剪切");
        cutItem.setOnAction(this::handleCut);

        MenuItem pasteItem = new MenuItem("粘贴");
        pasteItem.setOnAction(this::handlePaste);

        MenuItem renameItem = new MenuItem("重命名");
        renameItem.setOnAction(this::handleRename);

        MenuItem deleteItem = new MenuItem("删除");
        deleteItem.setOnAction(this::handleDelete);

        contextMenu.getItems().setAll(copyItem, cutItem, pasteItem, renameItem, deleteItem);

        thumbnailPane.setOnContextMenuRequested(event -> {
            boolean hasSelection = !selectedFiles.isEmpty();
            boolean canPaste = clipboardFiles.size() > 0;

            copyItem.setDisable(!hasSelection);
            cutItem.setDisable(!hasSelection);
            deleteItem.setDisable(!hasSelection);
            renameItem.setDisable(!hasSelection);
            pasteItem.setDisable(!canPaste);

            contextMenu.show(thumbnailPane, event.getScreenX(), event.getScreenY());
            event.consume();
        });

        // 自动收起菜单
        Platform.runLater(() -> {
            thumbnailPane.getScene().addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                if (contextMenu.isShowing() && e.getButton() != javafx.scene.input.MouseButton.SECONDARY) {
                    contextMenu.hide();
                }
            });
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
    //初始化按钮
    private void setupNavigationButtons() {
        backButton.setOnAction(event -> navigateBack());
        forwardButton.setOnAction(event -> navigateForward());
        upButton.setOnAction(event -> navigateUp());

        // 初始时禁用导航按钮
        updateNavigationButtons();
    }

    //文件导航
    private void navigateBack() {
        if (!navigationHistory.isEmpty()) {
            File currentDir = currentDirectory;
            forwardHistory.push(currentDir);
            currentDirectory = navigationHistory.pop();
            loadThumbnails(currentDirectory);
            updateDirectoryTreeSelection(currentDirectory);
            updateNavigationButtons();
        }
    }

    private void navigateForward() {
        if (!forwardHistory.isEmpty()) {
            File currentDir = currentDirectory;
            navigationHistory.push(currentDir);
            currentDirectory = forwardHistory.pop();
            loadThumbnails(currentDirectory);
            updateDirectoryTreeSelection(currentDirectory);
            updateNavigationButtons();
        }
    }

    private void navigateUp() {
        if (currentDirectory != null && currentDirectory.getParentFile() != null) {
            File currentDir = currentDirectory;
            navigationHistory.push(currentDir);
            forwardHistory.clear();
            currentDirectory = currentDirectory.getParentFile();
            loadThumbnails(currentDirectory);
            updateDirectoryTreeSelection(currentDirectory);
            updateNavigationButtons();
        }
    }

    private void updateNavigationButtons() {
        backButton.setDisable(navigationHistory.isEmpty());
        forwardButton.setDisable(forwardHistory.isEmpty());
        upButton.setDisable(currentDirectory == null || currentDirectory.getParentFile() == null);
    }

    private void loadThumbnails(File directory) {
        thumbnailPane.getChildren().clear();
        selectedFiles.clear();

        if (directory.isDirectory()) {
            currentDirectory = directory;
            selectedDirLabel.setText("当前文件: " + directory.getName());
            pathField.setText(directory.getAbsolutePath());

            String searchText = searchField.getText();
            File[] files = directory.listFiles((dir, name) -> {
                boolean isImage = imagePattern.matcher(name).matches();
                boolean matchesSearch = searchText.isEmpty() ||
                    name.toLowerCase().contains(searchText.toLowerCase());
                return isImage && matchesSearch;
            });

            if (files != null) {
                for (File file : files) {
                    VBox thumbnail = createThumbnail(file);
                    thumbnailPane.getChildren().add(thumbnail);
                }
                updateStatusMessage();
            }
        }
    }

    //搜索图片
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
      //  System.out.println("Selected file: " + file.getName() + ", selected count: " + selectedFiles.size());
        updateStatusMessage();
    }

    private void clearSelection() {
      //  System.out.println("Clearing selection, before count: " + selectedFiles.size());
        selectedFiles.clear();
        for (javafx.scene.Node node : thumbnailPane.getChildren()) {
            if (node instanceof VBox) {
                ((VBox) node).getStyleClass().remove("selected");
            }
        }
        updateStatusMessage();
    }

    private void updateStatusMessage() {
        long totalSize = 0;
        int imageCount = 0;
        for (javafx.scene.Node node : thumbnailPane.getChildren()) {
            if (node instanceof VBox && node.getUserData() instanceof File) {
                File file = (File) node.getUserData();
                totalSize += file.length();
                imageCount++;
            }
        }
        String sizeText = formatFileSize(totalSize);
        statusMessageLabel.setText(String.format("找到 %d 张图片，总大小: %s", imageCount, sizeText));
        statusMessageLabelText.setText(String.format("选中 %d 张图片", selectedFiles.size()));
    }

    //统计图片大小
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    private long calculateTotalSize(File[] files) {
        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }
        return totalSize / 1024;
    }


    // 在类中添加方法实现
    private void refreshDirectoryTree() {
        TreeItem<String> selectedItem = directoryTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            File dir = new File(selectedItem.getValue());
            loadDirectoryTree(selectedItem, dir); // 重新加载当前目录节点
        }
    }



    @FXML
    private void handleDelete(ActionEvent event) {
        if (!selectedFiles.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认删除");
            alert.setHeaderText("确定要删除选中的图片吗？");
            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) {
                // 1. 彻底移除缩略图节点
                thumbnailPane.getChildren().removeIf(node -> {
                    if (node instanceof VBox) {
                        File f = (File) ((VBox) node).getUserData();
                        return selectedFiles.contains(f);
                    }
                    return false;
                });

                // 2. 尝试解除ImageView绑定
                for (File file : selectedFiles) {
                    // 这里其实已经没有ImageView引用了
                }

                // 3. 强制GC
                System.gc();

                // 4. 删除文件
                List<File> failedFiles = new ArrayList<>();
                for (File file : selectedFiles) {
                    try {
                        Files.deleteIfExists(file.toPath());
                    } catch (IOException e) {
                        failedFiles.add(file);
                    }
                }

                // 5. 刷新
                loadThumbnails(currentDirectory);

                if (!failedFiles.isEmpty()) {
                    StringBuilder message = new StringBuilder("以下文件删除失败：\n");
                    for (File file : failedFiles) {
                        message.append(file.getName()).append("\n");
                    }
                    showAlert("删除结果", message.toString());
                }
            }
        }
    }

    @FXML
    private void handleCopy(ActionEvent event) {
        clipboardFiles = new ArrayList<>(selectedFiles);
    }

    // 剪切操作
    @FXML
    private void handleCut(ActionEvent event) {
        clipboardFiles = new ArrayList<>(selectedFiles);
        isCutOperation = true; // 设置为剪切模式
    }


    @FXML
    private void handlePaste(ActionEvent event) {
        // 1. 检查剪贴板和当前目录是否有效
        if (clipboardFiles.isEmpty() || currentDirectory == null) {
            showAlert("提示", "剪贴板为空或未选择目录！");
            return;
        }

        // 2. 遍历剪贴板中的所有文件
        for (File source : clipboardFiles) {
            try {
                Path sourcePath = source.toPath();
                String fileName = source.getName();
                Path targetPath = currentDirectory.toPath().resolve(fileName);

                // 3. 处理文件重名问题
                if (Files.exists(targetPath)) {
                    // 弹出对话框让用户选择操作
                    ButtonType overwrite = new ButtonType("覆盖");
                    ButtonType rename = new ButtonType("自动重命名");
                    ButtonType cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);

                    Alert conflictAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    conflictAlert.setTitle("文件冲突");
                    conflictAlert.setHeaderText("文件 " + fileName + " 已存在！");
                    conflictAlert.setContentText("请选择操作：");
                    conflictAlert.getButtonTypes().setAll(overwrite, rename, cancel);

                    Optional<ButtonType> result = conflictAlert.showAndWait();

                    // 用户选择取消则跳过当前文件
                    if (result.get() == cancel) continue;

                    // 用户选择自动重命名
                    if (result.get() == rename) {
                        fileName = getUniqueFileName(targetPath);
                        targetPath = currentDirectory.toPath().resolve(fileName);
                    }
                }

                // 4. 根据操作类型（复制/剪切）执行操作
                if (isCutOperation) {
                    Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    message.setText("剪切成功: " + fileName);
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    message.setText("复制成功: " + fileName);
                }

            } catch (AccessDeniedException e) {
                showAlert("权限错误", "无法操作文件: " + e.getMessage());
            } catch (IOException e) {
                showAlert("操作失败", "文件操作出错: " + e.getMessage());
            }
        }

        // 5. 操作完成后处理
        clipboardFiles.clear(); // 清空剪贴板
        isCutOperation = false; // 重置剪切标记
        loadThumbnails(currentDirectory); // 刷新界面
    }

    // 辅助方法：显示错误对话框
    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
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
                File file = selectedFiles.get(0);
                String nameWithoutExt = file.getName();
                int dot = nameWithoutExt.lastIndexOf('.');
                if (dot > 0) nameWithoutExt = nameWithoutExt.substring(0, dot);
                TextInputDialog dialog = new TextInputDialog(nameWithoutExt);
                dialog.setTitle("重命名文件");
                dialog.setHeaderText("输入新的文件名（不含后缀）");
                dialog.setContentText("文件名:");
                Optional<String> result = dialog.showAndWait();
                if (result.isPresent()) {
                    String ext = getFileExtension(file);
                    renameFile(file, result.get() + ext);
                }
            } else {
                // 批量重命名
                TextInputDialog prefixDialog = new TextInputDialog("NewName");
                prefixDialog.setTitle("批量重命名");
                prefixDialog.setHeaderText("输入名称前缀（不含后缀）");
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
                                String ext = getFileExtension(file);
                                String newName = prefix + String.format("%0" + digitCount + "d", index) + ext;
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

    private void accept(ButtonType response) {
        if (response == ButtonType.OK) {
            List<File> failedFiles = new ArrayList<>();

            // 强制解除所有缩略图资源绑定
            thumbnailPane.getChildren().forEach(node -> {
                if (node instanceof VBox) {
                    VBox vbox = (VBox) node;
                    vbox.getChildren().forEach(child -> {
                        if (child instanceof ImageView) {
                            ImageView iv = (ImageView) child;
                            iv.setImage(null); // 解除图像绑定
                        }
                    });
                }
            });

            // 强制删除操作
            for (File file : selectedFiles) {
                try {
                    // 方法 1：通过 FileChannel 强制解除锁定
                    try (FileChannel channel = FileChannel.open(
                            file.toPath(),
                            StandardOpenOption.WRITE,
                            StandardOpenOption.DELETE_ON_CLOSE)) {
                        // 强制独占访问
                    }

                    // 方法 2：使用 NIO 删除（优先）
                    Files.deleteIfExists(file.toPath());

                } catch (IOException e) {
                    // 终极方案：延迟删除（Windows 专用）
                    if (System.getProperty("os.name").contains("Windows")) {
                        try {
                            Runtime.getRuntime().exec(
                                    "cmd /c ping 127.0.0.1 -n 2 > nul && del /F /Q \""
                                            + file.getAbsolutePath() + "\""
                            );
                        } catch (IOException ex) {
                            failedFiles.add(file);
                        }
                    } else {
                        failedFiles.add(file);
                    }
                }
            }

            // 刷新界面
            Platform.runLater(() -> loadThumbnails(currentDirectory));
        }
    }
}