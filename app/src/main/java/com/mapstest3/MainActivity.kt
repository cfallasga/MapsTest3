package com.mapstest3

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.android.synthetic.main.activity_main.*
import com.google.maps.android.PolyUtil
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private val FROM_REQUEST_CODE = 1
        private val TO_REQUEST_CODE = 2
        private val TAG = "MainActivity"
    }

    private lateinit var mMap: GoogleMap

    private  var mMarkerFrom: Marker? = null
    private  var mMarkerTo: Marker? = null
    private lateinit var mFromLatLng: LatLng
    private lateinit var mToLatLng: LatLng

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupMap()
        setupPlaces()
    }

    private fun setupMap() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
         val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
         mapFragment.getMapAsync(this)
    }

    private fun setupPlaces(){
        Places.initialize(applicationContext,getString(R.string.google_maps_key))
        // Create a new PlacesClient instance
        val placesClient = Places.createClient(this)
        btnFrom.setOnClickListener{
            startAutoComplete(FROM_REQUEST_CODE)
        }
        btnTo.setOnClickListener{
            startAutoComplete(TO_REQUEST_CODE)
        }
    }

    private fun startAutoComplete(requestCode: Int){
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .build(this)
        startActivityForResult(intent, requestCode)
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FROM_REQUEST_CODE) {
            processAutocompleteResult(resultCode,data) {  place ->
                tvFrom.text = getString(R.string.label_from,place.address)
                place.latLng?.let {
                   mFromLatLng = it
              }
                setMarkerFrom(mFromLatLng)
           }
            return
        }else if (requestCode == TO_REQUEST_CODE){
            processAutocompleteResult(resultCode,data) {  place ->
                tvTo.text = getString(R.string.label_to,place.address)
                place.latLng?.let {
                    mToLatLng = it
                }
                setMarkerTo(mToLatLng)
                drawMap()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun processAutocompleteResult(
        resultCode:Int,data: Intent?,
        callback: (Place)-> Unit
    ){
        Log.i(TAG, "processAutocompleteResult")
        when (resultCode) {
            Activity.RESULT_OK -> {
                data?.let {
                    val place = Autocomplete.getPlaceFromIntent(data)
                    Log.i(TAG, "Place: ${place}")
                    callback(place)
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                data?.let {
                    val status = Autocomplete.getStatusFromIntent(data)
                    status.statusMessage?.let {
                            message -> Log.i(TAG, message)
                    }
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        //set xoom preferences
        mMap.setMinZoomPreference(15f)
        mMap.setMaxZoomPreference(20f)
    }

    private fun addMarker(latLng: LatLng,tile: String) : Marker {
        val markerOptions =  MarkerOptions()
            .position(latLng)
            .title(tile)
        //val sydney = LatLng(-34.0, 151.0)
        //addMarker(sydney,"Sydney")
        //Log.i(TAG, "marker: ${markerOptions}")
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
       return mMap.addMarker(markerOptions)!!
    }

    private fun setMarkerFrom(latLng: LatLng){
        // si el marcador ya existe lo elimina
          mMarkerFrom?.remove()
          mMarkerFrom =  addMarker(latLng,getString(R.string.marker_Title_From))

    }

    private fun setMarkerTo(latLng: LatLng){
        // si el marcador ya existe lo elimina
        mMarkerTo?.remove()

        mMarkerTo =  addMarker(latLng,getString(R.string.marker_Title_To))

    }

    private fun getURL(from : LatLng, to : LatLng) : String {
        val origin = "origin=" + from.latitude + "," + from.longitude
        val dest = "destination=" + to.latitude + "," + to.longitude
        val sensor = "sensor=false"
        val params = "$origin&$dest&$sensor"
        return "https://maps.googleapis.com/maps/api/directions/json?$params"
    }

    private fun drawMap(){

        val path: MutableList<List<LatLng>> = ArrayList()
        val urlDirections = "https://maps.googleapis.com/maps/api/directions/json?origin=${mFromLatLng.latitude},${mFromLatLng.longitude}&destination=${mToLatLng.latitude},${mToLatLng.longitude}&key=${getString(R.string.google_maps_key)}"
        val directionsRequest = object : StringRequest(Method.GET, urlDirections, Response.Listener<String> {
                response ->
            val jsonResponse = JSONObject(response)
            // Get routes
            val routes = jsonResponse.getJSONArray("routes")
            val legs = routes.getJSONObject(0).getJSONArray("legs")
            val steps = legs.getJSONObject(0).getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val points = steps.getJSONObject(i).getJSONObject("polyline").getString("points")
                path.add(PolyUtil.decode(points))
            }
            for (i in 0 until path.size) {
                this.mMap!!.addPolyline(
                    PolylineOptions()
                        .addAll(path[i])
                        .color(ContextCompat.getColor(this,R.color.kotlin))
                        .width(20f)
                )
            }
        }, Response.ErrorListener {
                _ ->
        }){}
        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(directionsRequest)

    }

}


