module org.example.image {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.image to javafx.fxml;
    exports org.example.image;
}