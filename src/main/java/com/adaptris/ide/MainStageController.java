package com.adaptris.ide;

import com.adaptris.ide.jmx.InterlokJmxHelper;
import com.adaptris.ide.node.ExternalConnection;
import com.adaptris.ide.node.ExternalNodeController;
import com.adaptris.ide.node.InterlokNodeController;
import com.adaptris.ide.selector.AdaptrisEndpointStaticModelBuilder;
import com.adaptris.ide.selector.SelectorController;
import com.adaptris.ide.selector.SelectorModel;
import com.adaptris.mgmt.cluster.ClusterInstance;
import com.adaptris.mgmt.cluster.jgroups.ClusterInstanceEventListener;
import com.adaptris.mgmt.cluster.jgroups.JGroupsListener;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainStageController implements ClusterInstanceEventListener {
    
  private Map<String, ClusterInstance> clusterInstances;
  
  // Key is combination of host and endpoint.
  private Set<ExternalConnection> externalConnections;
  
  private JGroupsListener clusterManager = new JGroupsListener();
  
  @FXML
  private TextField clusterName;

  private AnchorPane interlokNode;

  @FXML
  private AnchorPane networkPane;
  
  @FXML
  private Button clusterSearchButton;

  @FXML
  private Button newWizardButton;
  
  @FXML
  public void initialize() {
    clusterInstances = new HashMap<>();
    externalConnections = new HashSet<>();
    
    clusterSearchButton.setOnMouseClicked((event) -> {
      handleSearchCluster();
    });

    newWizardButton.setDisable(false);
    newWizardButton.setOnMouseClicked((event) ->
    {
      newWizardv2();
    });
  }

  private void drawNewClusterInstance(ClusterInstance instance) {
    try {
      InterlokNodeController controller = new InterlokNodeController();
      controller.setClusterInstance(instance);
      
      FXMLLoader loader = new FXMLLoader(getClass().getResource("node/InterlokNode.fxml"));
      loader.setController(controller);

      interlokNode = loader.load();
      interlokNode.getStylesheets().add("/main.css");
      interlokNode.setUserData(controller);

      networkPane.getChildren().add(interlokNode);
      interlokNode.setLayoutX(networkPane.getWidth() / 2 - interlokNode.getPrefHeight());
      interlokNode.setLayoutY(networkPane.getHeight() / 2 - interlokNode.getPrefHeight());

      try {
        Set<ExternalConnection> instanceExternalConnections = new InterlokJmxHelper().withMBeanServer(instance.getJmxAddress()).getInterlokConfig().getExternalConnections();
        instanceExternalConnections.forEach(externalConnection -> {
          if (!externalConnection.getTechnology().canBeShared() || !externalConnections.contains(externalConnection))
          {
            externalConnections.add(externalConnection);
            AnchorPane external = drawNewExternalConnection(externalConnection);
            connectInterlokToExternal(external);
          }
        });

      } catch (Exception e) {
        e.printStackTrace();
      }

    } catch (IOException e) {
      e.printStackTrace();
    }

    newWizardButton.setDisable(false);
  }

  private AnchorPane drawNewExternalConnection(ExternalConnection externalConnection) {
    try {
      ExternalNodeController controller = new ExternalNodeController(null);
      controller.setExternalConnection(externalConnection);
      
      FXMLLoader loader = new FXMLLoader(getClass().getResource("node/ExternalNode.fxml"));
      loader.setController(controller);
  
      AnchorPane externalInstancePane = loader.load();
      externalInstancePane.getStylesheets().add("/main.css");
      externalInstancePane.setUserData(controller);

      int y = (int)(externalInstancePane.getPrefHeight() / 2);
      for (Node child : networkPane.getChildren()) {
        Object node = child.getUserData();
        if (node instanceof ExternalNodeController)
        {
          ExternalNodeController nodeController = (ExternalNodeController)node;
          if (nodeController != null && nodeController.getExternalConnection().getDirection() == externalConnection.getDirection())
          {
            y += externalInstancePane.getPrefHeight() * 3 / 2;
          }
        }
      }

      networkPane.getChildren().add(externalInstancePane);
      externalInstancePane.setLayoutX((externalConnection.getDirection() == ExternalConnection.ConnectionDirection.CONSUMER ? 1 : 7) * (networkPane.getWidth() - externalInstancePane.getPrefWidth()) / 8);
      externalInstancePane.setLayoutY(y);
      return externalInstancePane;
    } catch (Exception ex) {
      ex.printStackTrace();
      return null;
    }
  }

  private void connectInterlokToExternal(AnchorPane endpoint)
  {
    if (endpoint == null)
    {
      return;
    }
    double clusterX = interlokNode.getLayoutX() + interlokNode.getPrefWidth() / 8;
    double clusterY = interlokNode.getLayoutY() + interlokNode.getPrefHeight() / 2;
    double externalX = endpoint.getLayoutX() + endpoint.getPrefWidth() / 8;
    double externalY = endpoint.getLayoutY() + endpoint.getPrefHeight() / 2;

    Line line = new Line(clusterX, clusterY, externalX, externalY);
    line.setStroke(Color.rgb(255, 255, 255));

    InterlokNodeController interlok = (InterlokNodeController)interlokNode.getUserData();
    ExternalNodeController external = (ExternalNodeController)endpoint.getUserData();
    interlok.addLineToExternalNode(line);
    external.setLineToInterlokNode(line);

    networkPane.getChildren().add(line);
  }

  private void handleSearchCluster() {
    // Stop and restart the cluster manager component.
    clusterInstances.clear();
    try {
      clusterManager.stop();
      clusterManager.setJGroupsClusterName(clusterName.getText());
      
      clusterManager.registerListener(this);
      clusterManager.start();
      
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  @Override
  public void clusterInstancePinged(ClusterInstance clusterInstance) {
    Platform.runLater(() -> {
      if (!clusterInstances.containsKey(clusterInstance.getClusterUuid().toString())) {
        clusterInstances.put(clusterInstance.getClusterUuid().toString(), clusterInstance);
        drawNewClusterInstance(clusterInstance);
      }
    });
  }

  private void newWizardv2() {
    Parent root;
    try {
      Stage stage = new Stage();
      stage.initStyle(StageStyle.UNDECORATED);

      com.adaptris.ide.wizard.WizardController controller = new com.adaptris.ide.wizard.WizardController();

      FXMLLoader loader = new FXMLLoader(getClass().getResource("wizard/Wizard.fxml"));
      loader.setController(controller);

      root = loader.load();
      Scene scene = new Scene(root);
      scene.getStylesheets().add("/test.css");
      stage.setScene(scene);
      stage.show();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void newWizard()
  {
    FXMLLoader l1 = new FXMLLoader(getClass().getResource("Wizard.fxml"));
    WizardController w1 = new WizardController(ExternalConnection.ConnectionDirection.CONSUMER);
    w1.setOnNext((consumer) ->
    {
      externalConnections.add(consumer);
      connectInterlokToExternal(drawNewExternalConnection(consumer));

      FXMLLoader l2 = new FXMLLoader(getClass().getResource("Wizard.fxml"));
      WizardController w2 = new WizardController(ExternalConnection.ConnectionDirection.PRODUCER);
      w2.setOnNext((producer) ->
      {
        externalConnections.add(producer);
        connectInterlokToExternal(drawNewExternalConnection(producer));

        // TODO more stuff

        String config = GenerateConfig.generate(consumer, producer);
        FXMLLoader l3 = new FXMLLoader(getClass().getResource("ViewConfig.fxml"));
        ViewConfigController w3 = new ViewConfigController(config);
        l3.setController(w3);
        showWindow(l3, "Configuration");

      });
      l2.setController(w2);
      showWindow(l2, "Wizard : Producer");
    });
    l1.setController(w1);
    showWindow(l1, "Wizard : Consumer");
  }

  private void showWindow(FXMLLoader loader, String title)
  {
    try
    {
      Parent root = loader.load();
      Stage stage = new Stage();
      stage.setTitle(title);
      Scene scene = new Scene(root);
      scene.getStylesheets().add("/main.css");
      stage.setScene(scene);
      stage.show();
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
  }
}
