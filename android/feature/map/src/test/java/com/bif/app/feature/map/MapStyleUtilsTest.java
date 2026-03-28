package com.bif.app.feature.map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.BackgroundLayer;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.FillLayer;
import org.maplibre.android.style.layers.Layer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class MapStyleUtilsTest {

    @Mock
    private Context mockContext;
    @Mock
    private Resources mockResources;
    @Mock
    private Configuration mockConfiguration;
    @Mock
    private Style mockStyle;

    @Mock
    private BackgroundLayer mockBackgroundLayer;
    @Mock
    private FillLayer mockFillLayer;
    @Mock
    private LineLayer mockLineLayer;
    @Mock
    private SymbolLayer mockSymbolLayer;
    @Mock
    private CircleLayer mockCircleLayer;

    @Before
    public void setUp() {
        when(mockContext.getResources()).thenReturn(mockResources);
        when(mockResources.getConfiguration()).thenReturn(mockConfiguration);

        when(mockBackgroundLayer.getId()).thenReturn("background");

        when(mockFillLayer.getId()).thenReturn("water");
        when(mockFillLayer.getSourceLayer()).thenReturn("water");

        when(mockLineLayer.getId()).thenReturn("road");
        when(mockLineLayer.getSourceLayer()).thenReturn("road");

        when(mockSymbolLayer.getId()).thenReturn("poi");
        when(mockSymbolLayer.getSourceLayer()).thenReturn("poi");

        when(mockCircleLayer.getId()).thenReturn("park");
        when(mockCircleLayer.getSourceLayer()).thenReturn("park");

        List<Layer> layers = Arrays.asList(
                mockBackgroundLayer,
                mockFillLayer,
                mockLineLayer,
                mockSymbolLayer,
                mockCircleLayer
        );
        when(mockStyle.getLayers()).thenReturn(layers);
    }

    @Test
    public void testApplyPaletteForCurrentMode_LightMode() {
        mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_NO;

        try (MockedStatic<Color> mockedColor = mockStatic(Color.class)) {
            mockedColor.when(() -> Color.parseColor(anyString())).thenReturn(0xFFFFFF);
            
            MapStyleUtils.applyPaletteForCurrentMode(mockContext, mockStyle);

            verify(mockBackgroundLayer).setProperties(any());
            verify(mockFillLayer).setProperties(any());
            verify(mockLineLayer).setProperties(any());
            verify(mockSymbolLayer, atLeastOnce()).setProperties(any());
            verify(mockCircleLayer).setProperties(any());
        }
    }

    @Test
    public void testApplyPaletteForCurrentMode_DarkMode() {
        mockConfiguration.uiMode = Configuration.UI_MODE_NIGHT_YES;

        try (MockedStatic<Color> mockedColor = mockStatic(Color.class)) {
            mockedColor.when(() -> Color.parseColor(anyString())).thenReturn(0x000000);
            
            MapStyleUtils.applyPaletteForCurrentMode(mockContext, mockStyle);

            verify(mockBackgroundLayer).setProperties(any());
            verify(mockFillLayer).setProperties(any());
            verify(mockLineLayer).setProperties(any());
            verify(mockSymbolLayer, atLeastOnce()).setProperties(any());
            verify(mockCircleLayer).setProperties(any());
        }
    }
}
