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
import javafx.scene.layout.VBox;

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
                    if (node instanceof javafx.scene.layout.VBox) {
                        javafx.scene.layout.VBox thumbnailBox = (javafx.scene.layout.VBox) node;
                        javafx.geometry.Bounds bounds = thumbnailBox.localToParent(thumbnailBox.getBoundsInLocal());
                        if (selectionRectangle.getBoundsInLocal().intersects(bounds)) {
                            File file = (File) thumbnailBox.getUserData();
                            selectImage((ImageView) thumbnailBox.getChildren().get(0), file);
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
            // 创建一个新的图片对象，从给定的文件路径加载图片，设置图片的初始尺寸为100x100像素
            Image image = new Image(new FileInputStream(file), 100, 100, true, true);
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true); // 保持图片的原始宽高比
            imageView.setSmooth(true); // 平滑缩放
            imageView.setCache(true); // 图片缓存

            // 创建一个新的Label对象，用于显示文件名
            Label label = new Label(file.getName());

            label.setMaxWidth(100); // 设置Label的最大宽度，使其可以自动调整以适应最长的文件名

            // 创建一个新的VBox对象，将ImageView和Label作为子节点添加进去
            VBox vbox = new VBox(imageView, label);
            vbox.setAlignment(Pos.CENTER); // 设置VBox的对齐方式为居中
            vbox.setSpacing(5); // 设置子节点之间的间距

            // 设置VBox的padding，为ImageView和Label提供一些内边距
            vbox.setPadding(new Insets(5, 5, 5, 5));

            vbox.setUserData(file); // 存储文件对象

            vbox.setOnMouseClicked(event -> {
                if (event.isControlDown()) {
                    toggleSelection(imageView, file);
                } else if (event.getClickCount() == 2) {
                    openSlideshow(file);
                } else {
                    clearSelection();
                    selectImage(imageView, file);
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
                toggleSelection(imageView, file);
            } else if (event.getClickCount() == 2) {
                openSlideshow(file);
            } else {
                clearSelection();
                selectImage(imageView, file);
            }
        });
        return vbox;
    }

    private void openSlideshow(File startFile) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Slideshow/slideshow-view.fxml"));
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

    private void loadThumbnails(File directory) {
        thumbnailPane.getChildren().clear();
        selectedFiles.clear();

        if (directory.isDirectory()) {
            selectedDirLabel.setText("当前目录: " + directory.getAbsolutePath());
            File[] files = directory.listFiles((dir, name) -> imagePattern.matcher(name).matches());

            if (files != null) {
               // statusMessage.set("找到 " + files.length + " 张图片，总大小: " + calculateTotalSize(files) + " KB");
                statusMessageLabel.setText("找到 " + files.length + " 张图片，总大小: " + calculateTotalSize(files) + " KB");
                for (File file : files) {
                    VBox thumbnail = createThumbnail(file);
                    thumbnailPane.getChildren().add(thumbnail);
                }
            }
        }
    }

    private void toggleSelection(ImageView imageView, File file) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file);
            imageView.setStyle("-fx-border-color: none;");
        } else {
            selectedFiles.add(file);
            imageView.setStyle("-fx-border-color: blue; -fx-border-width: 2px;");
        }
        updateStatusMessage();
    }

    private void selectImage(ImageView imageView, File file) {
        selectedFiles.add(file);
        imageView.setStyle("-fx-border-color: blue; -fx-border-width: 2px;");
        updateStatusMessage();
    }

    private void clearSelection() {
        selectedFiles.clear();
        for (javafx.scene.Node node : thumbnailPane.getChildren()) {
            if (node instanceof ImageView) {
                ((ImageView) node).setStyle("-fx-border-color: none;");
            }
        }
        updateStatusMessage();
    }

    private void updateStatusMessage() {
        statusMessageLabelText.setText("选中 " + selectedFiles.size() + " 张图片");
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

    // private void openSlideshow(File startFile) {
    // 实现幻灯片播放窗口
    // }
}