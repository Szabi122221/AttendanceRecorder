package com.university.attendance;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Hallgató kezelő osztály - új hallgatók hozzáadása a rendszerhez

 * Ez az osztály felelős az új hallgatók rendszerbe viteléért, validációért és adatbázisba mentéséért.
 */
public class StudentManager
{
    private DatabaseManager dbManager;

    /**
     * Konstruktor
     * @param dbManager Adatbázis kezelő
     */
    public StudentManager(DatabaseManager dbManager)
    {
        this.dbManager = dbManager;
    }

    /**
     * Hallgató hozzáadása form megjelenítése
     */
    public void showAddStudentForm()
    {
        Stage formStage = new Stage();
        formStage.setTitle("Hallgató felvitele");

        VBox formBox = new VBox(15);
        formBox.setPadding(new Insets(20));

        Label titleLabel = new Label("Új hallgató hozzáadása a rendszerhez");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Form mezők
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Label nameLabel = new Label("Név:");
        TextField nameField = new TextField();
        nameField.setPromptText("pl. Kovács János");

        Label majorLabel = new Label("Szak:");
        TextField majorField = new TextField();
        majorField.setPromptText("pl. Informatika");

        Label neptunLabel = new Label("Neptun kód:");
        TextField neptunField = new TextField();
        neptunField.setPromptText("pl. ABC123");

        grid.add(nameLabel, 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(majorLabel, 0, 1);
        grid.add(majorField, 1, 1);
        grid.add(neptunLabel, 0, 2);
        grid.add(neptunField, 1, 2);

        // Gombok
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        Button saveButton = new Button("Mentés");
        Button cancelButton = new Button("Mégse");

        saveButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String major = majorField.getText().trim();
            String neptun = neptunField.getText().trim().toUpperCase();

            // Validáció
            if (name.isEmpty() || major.isEmpty() || neptun.isEmpty())
            {
                showAlert("Hiányzó adatok", "Minden mezőt ki kell tölteni!");
                return;
            }

            // Neptun kód hossz ellenőrzése (6 karakter)
            if (neptun.length() != 6)
            {
                showAlert("Hibás formátum", "A Neptun kód 6 karakter hosszú legyen (pl. ABC123, HU5A16)");
                return;
            }

            // Ellenőrzés, hogy már létezik-e
            if (dbManager.hasStudent(neptun))
            {
                showAlert("Már létezik", "Ez a Neptun kód már szerepel a rendszerben: " + neptun);
                return;
            }

            // Hozzáadás az adatbázishoz
            dbManager.insertStudent(name, major, neptun);

            showAlert("Siker", "Hallgató hozzáadva: " + name + " (" + neptun + ")");
            formStage.close();
        });

        cancelButton.setOnAction(e -> formStage.close());

        buttonBox.getChildren().addAll(saveButton, cancelButton);

        formBox.getChildren().addAll(titleLabel, grid, buttonBox);

        Scene scene = new Scene(formBox, 400, 250);
        formStage.setScene(scene);
        formStage.show();
    }




    /**
     * Alert ablak megjelenítése, ha esetleg máshol (esetleg máshog is megkelle jeleníteni
     */
    private void showAlert(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
