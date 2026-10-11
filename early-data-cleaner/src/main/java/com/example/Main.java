package com.example;

public class Main {
    public static void main(String[] args) {
        DataCleaner.cleanData("Book1.csv", "cleaned_data.csv");
        // ModelTrainer.trainModel("data/processed/cleaned_data.csv");
        // ModelTuner.tuneHyperparameters();
        // ModelEvaluator.evaluate();
        System.out.println("Data cleaning finished.");
    }
}