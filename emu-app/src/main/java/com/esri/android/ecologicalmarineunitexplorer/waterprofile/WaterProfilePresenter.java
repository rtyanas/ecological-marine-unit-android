/* Copyright 2017 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For additional information, contact:
 * Environmental Systems Research Institute, Inc.
 * Attn: Contracts Dept
 * 380 New York Street
 * Redlands, California, USA 92373
 *
 * email: contracts@esri.com
 *
 */
package com.esri.android.ecologicalmarineunitexplorer.waterprofile;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.util.Log;
import com.esri.android.ecologicalmarineunitexplorer.data.*;
import com.esri.android.ecologicalmarineunitexplorer.util.EmuHelper;
import com.esri.arcgisruntime.geometry.Point;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.formatter.IFillFormatter;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.*;

/**
 * This is the concrete implementation of the Presenter defined in the WaterProfileContract.Presenter.
 * It encapsulates business logic and drives the behavior of the View.
 */

public class WaterProfilePresenter implements WaterProfileContract.Presenter {
  private final Point mColumnLocation;
  private final WaterProfileContract.View mView;
  private final DataManager mDataManager;
  private final Map<String, ScatterData> mChartData = new HashMap<>();
  private final String TAG = WaterProfilePresenter.class.getSimpleName();

  public WaterProfilePresenter(@NonNull Point p, @NonNull WaterProfileContract.View view, @NonNull DataManager dataManager) {
    mColumnLocation = p;
    mView = view;
    mView.setPresenter(this);
    mDataManager = dataManager;
  }


  @Override public void getWaterProfiles(Point point) {
    mView.showProgressBar("Building scatter plots", "Preparing Water Profile");
    mDataManager.queryForEmuColumnProfile(mColumnLocation, new ServiceApi.ColumnProfileCallback() {
      @Override public void onProfileLoaded(WaterProfile waterProfile) {
        if (waterProfile.measurementCount() > 0){

          List<CombinedData> combinedDataList = new ArrayList<CombinedData>();

          combinedDataList.add(buildCombinedData(waterProfile,"TEMPERATURE"));
          combinedDataList.add(buildCombinedData(waterProfile,"SALINITY"));
          combinedDataList.add(buildCombinedData(waterProfile,"DISSOLVED_OXYGEN"));
          combinedDataList.add(buildCombinedData(waterProfile,"PHOSPHATE"));
          combinedDataList.add(buildCombinedData(waterProfile,"SILICATE"));
          combinedDataList.add(buildCombinedData(waterProfile,"NITRATE"));

          mView.showWaterProfiles(combinedDataList);
        }else{
          // Notify user
          mView.showMessage("No profile data found");
        }
        mView.hideProgressBar();
      }
    });
  }

  private CombinedData buildCombinedData(WaterProfile waterProfile, String property){
    CombinedData data = new CombinedData();
    ScatterData scatterData = buildScatterDataForProperty(waterProfile, property);
    data.setData(scatterData);
    LineData emuLayerData = buildEMULayers(data.getXMin() - 1, data.getXMax() + 1);
    data.setData(emuLayerData);
    return data;

  }
  @Override public void start() {
    getWaterProfiles(mColumnLocation);

  }
  private ScatterData buildScatterDataForProperty(WaterProfile profile, String property){
    ScatterData data = new ScatterData();

    if (profile != null){
      // Get all the measurements for the property
      Map<Double,Double> propertyMeasurementByDepth = profile.getMeasurementsForProperty(property);
      ArrayList<Entry> entries = new ArrayList<>();
      Set<Double> depths = propertyMeasurementByDepth.keySet();
      for (Double depth : depths){
        float y = (float) Math.abs(depth);
        float x = (float) propertyMeasurementByDepth.get(depth).doubleValue();
        entries.add(new Entry(x, y));
      }

      ScatterDataSet set = new ScatterDataSet(entries, property);
      set.setColor(Color.BLACK);
      set.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
      set.setScatterShapeSize(20f);
      set.setDrawValues(false);
      data.addDataSet(set);
    }else{
      Log.e(TAG, "Profile data object is null!");
    }

    return  data;
  }
  private LineData buildEMULayers(float xmin, float xmax){
    LineData data = new LineData();
    WaterColumn column = mDataManager.getCurrentWaterColumn();

    if (column != null){
      Set<EMUObservation> observations = column.getEmuSet();
      for (final EMUObservation observation : observations){
        ArrayList<Entry> entries = new ArrayList<>();

        for (float index = xmin; index <= xmax; index++) {
          entries.add(new Entry(index, Math.abs(observation.getTop())));
        }

        LineDataSet set = new LineDataSet(entries, "Line DataSet");
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setFillColor(Color.parseColor(EmuHelper.getColorForEMUCluster(observation.getEmu().getName())));

        set.setFillAlpha(255);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setDrawFilled(true);
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setDrawCircleHole(false);
        set.setFillFormatter(new IFillFormatter() {
          @Override
          public float getFillLinePosition(ILineDataSet dataSet, LineDataProvider dataProvider) {
            return Math.abs(observation.getTop()) + observation.getThickness();
          }
        });
        data.addDataSet(set);
      }
    }else{
      Log.e(TAG, "Water column data object is null!");
    }


    return data;
  }

}
