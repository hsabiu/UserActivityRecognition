package com.habib.wekaserver;

import java.io.File;

import weka.classifiers.lazy.IBk;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

class DataClassifier {

    private static IBk KNNClassifier;
    private static Instances newDataset;

    void createModel() {
        // load model from the file system
        try {
            File modelFile = new File("src/main/resources/KNNModel.model");
            KNNClassifier = (IBk) weka.core.SerializationHelper.read(modelFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void loadDataset(String datasetPath) {
        // load new dataset
        try {
            DataSource newDataSource = new DataSource(datasetPath);
            newDataset = newDataSource.getDataSet();
            // set class index
            newDataset.setClassIndex(newDataset.numAttributes() - 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    String getClassValue() {

        String classValue = null;

        try {
            // get instance object of current instance
            Instance newInst = newDataset.instance(0);
            // call classifyInstance, which returns a double value for the class
            double doubleClassValue = KNNClassifier.classifyInstance(newInst);
            // use this value to get string value of the predicted class
            classValue = newDataset.classAttribute().value((int) doubleClassValue);
            //System.out.println("Class predicted: " + classValue);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return classValue;
    }

    void deleteFile(String filePath) {
        File file = new File(filePath);
        file.delete();
    }
}