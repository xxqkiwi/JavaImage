module org.example.image {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.image to javafx.fxml;
    //exports org.example.image;
    exports org.example.image.Application;
    opens org.example.image.Application to javafx.fxml;
    exports org.example.image.Controller;
    opens org.example.image.Controller to javafx.fxml;
}