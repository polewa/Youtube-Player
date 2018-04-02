package pl.polew.youtufy.controller;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebEngine;
import pl.polew.youtufy.data.YouTubePlayer;
import pl.polew.youtufy.data.YoutubeVideo;
import pl.polew.youtufy.main.Youtube;
 
public class MainController implements Initializable {
    
    @FXML
    private ContentPaneController contentPaneController;
     
    @FXML
    private ControlPaneController controlPaneController;
     
    @FXML
    private MenuPaneController menuPaneController;
     
    @FXML
    private SearchPaneController searchPaneController;
     
    private Youtube youtubeInstance;
    
    private void configureButtons() {
    	ToggleButton playButton = controlPaneController.getPlayButton();
    	WebEngine webEngine = contentPaneController.getVideoWebView().getEngine();
    	
    	playButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				if (playButton.isSelected()) {
					webEngine.executeScript("player.playVideo();");
				} else {
					webEngine.executeScript("player.pauseVideo();");
				}
			}
		});
    	
    	Button prevButton = controlPaneController.getPreviousButton();
    	Button nextButton = controlPaneController.getNextButton();
    	TableView<YoutubeVideo> resultsTable = contentPaneController.getResultTableView();
    	
    	prevButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				int index = resultsTable.getSelectionModel().getSelectedIndex();
				if (index > 0) {
					resultsTable.getSelectionModel().select(index - 1);
					contentPaneController.playSelectedItem();
				}
			}
		});
    	
    	nextButton.setOnAction(new EventHandler<ActionEvent>() {

			@Override
			public void handle(ActionEvent event) {
				int index = resultsTable.getSelectionModel().getSelectedIndex();
				int size = resultsTable.getItems().size();
				if (index < size - 1) {
					resultsTable.getSelectionModel().select(index + 1);
					contentPaneController.playSelectedItem();
				}
			}
		});
    }
    
    private void configureTableClick() {
    	TableView<YoutubeVideo> resultsTable = contentPaneController.getResultTableView();
    	resultsTable.addEventFilter(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {

			@Override
			public void handle(MouseEvent event) {
				if (event.getClickCount() == 2) {
					contentPaneController.playSelectedItem();
					controlPaneController.getPlayButton().setSelected(true);
				}
			}
		});
    }
    
    private void configureVolumeControl() {
    	Slider volumeSlider = controlPaneController.getVolumeSlider();
    	volumeSlider.setMin(0);
    	volumeSlider.setMax(100);
    	volumeSlider.valueProperty().addListener(new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
				double volume = newValue.doubleValue();
				contentPaneController.getVideoWebView().getEngine().executeScript("player.setVolume(" + volume + ")");
				
			}
    		
    	});
    }
    
    private void calculateDuration() {
    	WebEngine engine = contentPaneController.getVideoWebView().getEngine();
    	YouTubePlayer.PLAYING.set(false);
    	Task<Void> durationTask = new Task<Void>() {

			@Override
			protected Void call() throws Exception {
				DoubleProperty durationProperty = new SimpleDoubleProperty();
				durationProperty.set(0.0);
				while(durationProperty.get() == 0) {
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							Object o = engine.executeScript("player.getDuration();");
							if (o.getClass().equals(Integer.class) || o.getClass().equals(Double.class)) {
								Number duration = (Number) o;
								durationProperty.set(duration.doubleValue());
							}
						}
					});
					Thread.sleep(1000);
				}
				Slider progressSlider = controlPaneController.getSongSlider();
				Platform.runLater(() -> progressSlider.setMax(durationProperty.get()));
				YouTubePlayer.PLAYING.set(true);
				return null;
			}
    		
    	};
    	Thread t = new Thread(durationTask);
    	t.setDaemon(true);
    	t.start();
    }
    
    private void configureProgressSlider() {
    	Slider progressSlider = controlPaneController.getSongSlider();
    	WebEngine engine = contentPaneController.getVideoWebView().getEngine();
    	progressSlider.setOnMouseReleased(new EventHandler<MouseEvent>() {

			@Override
			public void handle(MouseEvent event) {
				int state = (Integer) engine.executeScript("player.getPlayerState();");
				if (state == YouTubePlayer.PlayerState.PLAYING.getState()
						|| state == YouTubePlayer.PlayerState.PAUSED.getState()) {
					engine.executeScript("player.seekTo(" + progressSlider.getValue() + ");");
				}
				
			}
		});
    }
    
    
    @Override
    public void initialize(URL arg0, ResourceBundle arg1) {
        TextField searchField = searchPaneController.getSearchTextField();
        TableView<YoutubeVideo> resultsTable = contentPaneController.getResultTableView();
         
        youtubeInstance = new Youtube();
        youtubeInstance.getSearchQuery().bind(searchField.textProperty());
        resultsTable.setItems(youtubeInstance.getYoutubeVideos());
         
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if(event.getCode() == KeyCode.ENTER) {
                    try {
                        youtubeInstance.search();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
         
        ListView<String> searchHistory = searchPaneController.getHistoryListView();
        searchHistory.addEventFilter(MouseEvent.MOUSE_CLICKED, new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if(event.getClickCount() == 2) {
                    String searchText = searchHistory.getSelectionModel().getSelectedItem();
                    searchField.setText(searchText);
                    try {
                        youtubeInstance.search();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        });
        
        configureButtons();
        configureTableClick();
        configureVolumeControl();
        calculateDuration();
        configureProgressSlider();
    }
}