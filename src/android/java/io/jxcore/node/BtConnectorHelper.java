package io.jxcore.node;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thaliproject.p2p.btconnectorlib.BTConnector;
import org.thaliproject.p2p.btconnectorlib.BTConnectorSettings;
import org.thaliproject.p2p.btconnectorlib.ServiceItem;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Created by juksilve on 14.5.2015.
 */
public class BtConnectorHelper implements BTConnector.Callback, BTConnector.ConnectSelector, BtSocketDisconnectedCallBack {

    Context context = null;

    final String serviceTypeIdentifier = "Cordovap2p._tcp";
    final String BtUUID                = "fa87c0d0-afac-11de-8a39-0800200c9a66";
    final String Bt_NAME               = "Thaili_Bluetooth";


    CopyOnWriteArrayList<ServiceItem> lastAvailableList = new CopyOnWriteArrayList<ServiceItem>();

    BTConnectorSettings conSettings = null;
    BTConnector mBTConnector = null;

    CopyOnWriteArrayList<BtToServerSocket> mServerSocketList = new CopyOnWriteArrayList<BtToServerSocket>();
    BtToRequestSocket mBtToRequestSocket = null;
    String myPeerIdentifier= "";
    String myPeerName = "";

    int mServerPort = 0;

    public BtConnectorHelper() {
        conSettings = new BTConnectorSettings();
        conSettings.SERVICE_TYPE = serviceTypeIdentifier;
        conSettings.MY_UUID = UUID.fromString(BtUUID);
        conSettings.MY_NAME = Bt_NAME;
        this.context = jxcore.activity.getBaseContext();
    }

    public BTConnector.WifiBtStatus Start(String peerName,int port){
        this.mServerPort = port;
        this.myPeerIdentifier= GetBluetoothAddress();
        this.myPeerName = peerName;
        this.lastAvailableList.clear();

        Stop();

        BTConnector tmpCon= new BTConnector(context,this,this,conSettings);
        BTConnector.WifiBtStatus  ret = tmpCon.Start(this.myPeerIdentifier,this.myPeerName);
        mBTConnector = tmpCon;
        return ret;
    }

    public boolean isRunning(){
        if(mBTConnector != null){
            return true;
        }else{
            return false;
        }
    }
    public void Stop(){

        BTConnector tmpCon = mBTConnector;
        mBTConnector = null;
        if(tmpCon != null){
            tmpCon.Stop();
        }

        //disconnect all incoming connections
        DisconnectIncomingConnections();

        // disconnect outgoing connection
        Disconnect("");
    }

    // we only cut off our outgoing connections, incoming ones are cut off from the other end.
    // if we want to cut off whole communications, we'll do Stop
    public boolean Disconnect(String peerId){
        BtToRequestSocket tmpSoc = mBtToRequestSocket;
        if(tmpSoc == null) {
            return false;
        }

        String currentpeerId = tmpSoc.GetPeerId();
        print_debug("Disconnect : " + peerId + ", current request : " + currentpeerId);
        if (peerId.length() == 0 || peerId.equalsIgnoreCase(currentpeerId)) {
            print_debug("Disconnect:::Stop :" + currentpeerId);

            tmpSoc.Stop();
            mBtToRequestSocket = null;

            return true;

        }
        return false;
    }

    //test time implementation to simulate 'peer disappearing'
    public boolean  DisconnectIncomingConnections() {

        if (mServerSocketList == null) {
            return false;
        }

        for (int i = 0; i < mServerSocketList.size(); i++) {
            BtToServerSocket tmpToSrvSocket = mServerSocketList.get(i);
            if (tmpToSrvSocket != null) {
                print_debug("Disconnect:::Stop : mBtToServerSocket :" + tmpToSrvSocket.getName());
                tmpToSrvSocket.Stop();
                mServerSocketList.remove(i);
            }
        }

        mServerSocketList.clear();

        return true;

    }

    public String GetBluetoothAddress(){
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        return bluetooth == null ? null : bluetooth.getAddress();
    }

    public String GetDeviceName(){
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        return bluetooth == null ? null : bluetooth.getName();
    }

    public boolean SetDeviceName(String name){
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        return bluetooth == null ? null : bluetooth.setName(name);
    }

    ConnectStatusCallback mConnectStatusCallback = null;

    public interface ConnectStatusCallback{
        void ConnectionStatusUpdate(String Error, int port);
    }

    public void BeginConnectPeer(String toPeerId, ConnectStatusCallback connectStatusCallback) {
        ConnectStatusCallback tmpCallback = connectStatusCallback;
        if (tmpCallback == null) {
            //nothing we should do, since we can not update progress
            print_debug("BeginConnectPeer callback is NULL !!!!!!");
            return;
        }

        BtToRequestSocket tmpToReqSoc = mBtToRequestSocket;
        if (tmpToReqSoc != null) {
            tmpCallback.ConnectionStatusUpdate("Already connected to " + tmpToReqSoc.GetPeerId(), -1);
            return;
        }

        ServiceItem selectedDevice = null;
        if (lastAvailableList != null) {
            for (int i = 0; i < lastAvailableList.size(); i++) {
                if (lastAvailableList.get(i).peerId.contentEquals(toPeerId)) {
                    selectedDevice = lastAvailableList.get(i);
                    break;
                }
            }
        }

        if (selectedDevice == null) {
            tmpCallback.ConnectionStatusUpdate("Device Address for " + toPeerId + " not found from Discovered device list.", -1);
            return;
        }

        BTConnector tmpConn = mBTConnector;
        if (tmpConn == null) {
            tmpCallback.ConnectionStatusUpdate("Device connectivity not started, please call StartBroadcasting before attempting to connect", -1);
            return;
        }

        BTConnector.TryConnectReturnValues retVal = tmpConn.TryConnect(selectedDevice);
        if (retVal == BTConnector.TryConnectReturnValues.Connecting) {
            //all is ok, lets wait callbacks, and for them lets copy the callback here
            mConnectStatusCallback = tmpCallback;
        } else if (retVal == BTConnector.TryConnectReturnValues.NoSelectedDevice) {
            // we do check this already, thus we should not get this ever.
            tmpCallback.ConnectionStatusUpdate("Device Address for " + toPeerId + " not found from Discovered device list.", -1);
        } else if (retVal == BTConnector.TryConnectReturnValues.AlreadyAttemptingToConnect) {
            tmpCallback.ConnectionStatusUpdate("There is already one connection attempt progressing.", -1);
        } else if (retVal == BTConnector.TryConnectReturnValues.BTDeviceFetchFailed) {
            tmpCallback.ConnectionStatusUpdate("Bluetooth API failed to get Bluetooth device for the address : " + selectedDevice.peerAddress, -1);
        }
    }

    //this is always called in context of thread that created instance of the library
    @Override
    public void Connected(BluetoothSocket bluetoothSocket, boolean incoming,String peerId,String peerName,String peerAddress) {

        if (bluetoothSocket == null) {
            return;
        }

        // this is here, so if we have not found the incoming peer via Discovery, we'll get it
        // added to the discovery list, and we can connect back to it.
        AddPeerIfNotDiscovered(bluetoothSocket, peerId, peerName, peerAddress);
        print_debug("Starting the connected thread incoming : " + incoming + ", " + peerName);

        if (incoming) {
            BtToServerSocket tmpBtToServerSocket = new BtToServerSocket(bluetoothSocket, this);

            mServerSocketList.add(tmpBtToServerSocket);

            tmpBtToServerSocket.SetIdAddressAndName(peerId, peerName, peerAddress);
            tmpBtToServerSocket.setPort(this.mServerPort);
            tmpBtToServerSocket.start();

            int port = tmpBtToServerSocket.GetLocalHostPort();
            print_debug("Server socket is using : " + port + ", and is now connected.");

            return;
        }

        //not incoming, thus its outgoing

        // basically we should never get to make successful connection
        // if we already have one outgoing, so the old if it would somehow be there
        // would be invalid, so lets get rid of it if we are having new outgoing connection.
        BtToRequestSocket tmpregSoc = mBtToRequestSocket;
        mBtToRequestSocket = null;
        if (tmpregSoc != null) {
            tmpregSoc.Stop();
        }

        tmpregSoc = new BtToRequestSocket(bluetoothSocket, new BtSocketDisconnectedCallBack() {
            //Called when disconnect event happens, so we can stop & clean everything now.
            @Override
            public void Disconnected(Thread who, String Error) {

                BtToRequestSocket tmpSoc = mBtToRequestSocket;
                mBtToRequestSocket = null;
                if (tmpSoc != null) {
                    print_debug("BT Request socket disconnected");
                    tmpSoc.Stop();
                }
            }
        }, new BtToRequestSocket.ReadyForIncoming() {
            // there is a good chance on race condition where the node.js gets to do their client socket
            // before we got into the accept line executed, thus this callback takes care that we are ready before node.js is
            @Override
            public void listeningAndAcceptingNow(int port) {
                final int portTmp = port;
                print_debug("Request socket is using : " + portTmp);
                new Handler(jxcore.activity.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ConnectStatusCallback tmpCallBack = mConnectStatusCallback;
                        if (tmpCallBack != null) {
                            print_debug("Calling ConnectionStatusUpdate with port :" + portTmp);
                            tmpCallBack.ConnectionStatusUpdate(null, portTmp);
                        }
                    }
                }, 300);
            }
        });
        tmpregSoc.SetIdAddressAndName(peerId, peerName, peerAddress);
        tmpregSoc.setPort(0);// setting port to zero, should automatically assign it later on.
        tmpregSoc.start();
        mBtToRequestSocket = tmpregSoc;
    }


    //This is only called for mBtToServerSocket classes, since mBtToRequestSocket handles it in its own instance callback
    @Override
    public void Disconnected(Thread who, String Error) {

        print_debug("BT Disconnected with error : " + Error);
        if (mServerSocketList != null && who != null) {
            for (int i = 0; i < mServerSocketList.size(); i++) {
                BtToServerSocket tmpToSrvSoc = mServerSocketList.get(i);
                if (tmpToSrvSoc != null && (tmpToSrvSoc.getId() == who.getId())) {
                    print_debug("Disconnect:::Stop : mBtToServerSocket :" + tmpToSrvSoc.GetPeerName());
                    tmpToSrvSoc.Stop();
                    mServerSocketList.remove(i);
                    break;
                }
            }
        }
    }

    // if the peer that just made incoming connection has not been discovered yet, we'll ad it here
    // thus allowing us to make connection back to it
    public void AddPeerIfNotDiscovered(BluetoothSocket bluetoothSocket, String peerId,String peerName,String peerAddress) {

        if (lastAvailableList == null) {
            lastAvailableList = new CopyOnWriteArrayList<ServiceItem>();
        }

        boolean isDiscovered = false;

        for (int i = 0; i < lastAvailableList.size(); i++) {
            if (lastAvailableList.get(i).peerId.contentEquals(peerId)) {
                isDiscovered = true;
                break;
            }
        }

        if (!isDiscovered) {
            String BtAddress = peerAddress;
            if (bluetoothSocket != null) {
                if (bluetoothSocket.getRemoteDevice() != null) {
                    BtAddress = bluetoothSocket.getRemoteDevice().getAddress();
                }
            }

            ServiceItem tmpSrv = new ServiceItem(peerId, peerName, BtAddress, "", "", "");
            lastAvailableList.add(tmpSrv);

            JSONArray jsonArray = new JSONArray();
            jsonArray.put(getAvailabilityStatus(tmpSrv, true));
            jxcore.CallJSMethod(JXcoreExtension.EVENTSTRING_PEERAVAILABILITY, jsonArray.toString());
        }
    }


    //this is always called in context of thread that created instance of the library
    @Override
    public void ConnectionFailed(String peerId, String peerName, String peerAddress) {
        ConnectStatusCallback tmpStatBack = mConnectStatusCallback;
        if(tmpStatBack != null) {
            tmpStatBack.ConnectionStatusUpdate("Connection to " + peerId + " failed", -1);
        }
    }

    //this is always called in context of thread that created instance of the library
    @Override
    public void StateChanged(BTConnector.State state) {

        // with this version, we don't  use this state information for anything
        switch (state) {
            case Idle:
                break;
            case NotInitialized:
                break;
            case WaitingStateChange:
                break;
            case FindingPeers:
                break;
            case FindingServices:
                break;
            case Connecting:
                break;
            case Connected:
                break;
        };

    }

    // this is called with a full list of peer-services we see, its takes time to get,
    // since there is time spend between each peer we discover
    // anyway, this list can be used for determining whether the peer we saw earlier has now disappeared
    // will be called null or empty list, if no services are found during some time period.

    //this is always called in context of thread that created instance of the library
    @Override
    public ServiceItem CurrentPeersList(List<ServiceItem> serviceItems) {

        Boolean wasPrevouslyAvailable = false;

        JSONArray jsonArray = new JSONArray();

        if (serviceItems != null) {
            for (int i = 0; i < serviceItems.size(); i++) {

                wasPrevouslyAvailable = false;
                ServiceItem item = serviceItems.get(i);
                if (lastAvailableList != null) {
                    for (int ll = (lastAvailableList.size() - 1); ll >= 0; ll--) {
                        if (item.deviceAddress.equalsIgnoreCase(lastAvailableList.get(ll).deviceAddress)) {
                            wasPrevouslyAvailable = true;
                            lastAvailableList.remove(ll);
                        }
                    }
                }

                if (!wasPrevouslyAvailable) {
                    jsonArray.put(getAvailabilityStatus(item, true));
                }
            }
        }

        if (lastAvailableList != null) {
            for (int ii = 0; ii < lastAvailableList.size(); ii++) {
                jsonArray.put(getAvailabilityStatus(lastAvailableList.get(ii), false));
                lastAvailableList.remove(ii);
            }
        }

        if (serviceItems != null) {
            for (int iii = 0; iii < serviceItems.size(); iii++) {
                lastAvailableList.add(serviceItems.get(iii));
            }
        }

        // lets not sent any empty arrays up.
        if (jsonArray.toString().length() > 5) {
            jxcore.CallJSMethod(JXcoreExtension.EVENTSTRING_PEERAVAILABILITY, jsonArray.toString());
        }
        return null;
    }


    // this is called when we see a peer, so we can inform the app of its availability right when we see it
    //this is always called in context of thread that created instance of the library
    @Override
    public void PeerDiscovered(ServiceItem serviceItem) {
        boolean wasPrevouslyAvailable = false;

        if (lastAvailableList != null) {
            for (int ll = (lastAvailableList.size() - 1); ll >= 0; ll--) {
                if (serviceItem.deviceAddress.equalsIgnoreCase(lastAvailableList.get(ll).deviceAddress)) {
                    wasPrevouslyAvailable = true;
                }
            }
        }

        if (!wasPrevouslyAvailable) {

            lastAvailableList.add(serviceItem);

            JSONArray jsonArray = new JSONArray();
            jsonArray.put(getAvailabilityStatus(serviceItem, true));
            jxcore.CallJSMethod(JXcoreExtension.EVENTSTRING_PEERAVAILABILITY, jsonArray.toString());
        }
    }


    private JSONObject getAvailabilityStatus(ServiceItem item, boolean available) {

        JSONObject returnJsonObj = new JSONObject();
        try {
            returnJsonObj.put(JXcoreExtension.EVENTVALUESTRING_PEERID, item.peerId);
            returnJsonObj.put(JXcoreExtension.EVENTVALUESTRING_PEERNAME, item.peerName);
            returnJsonObj.put(JXcoreExtension.EVENTVALUESTRING_PEERAVAILABLE, available);
        } catch (JSONException e) {
            print_debug("JSONException : " + e.toString());
        }
        return returnJsonObj;
    }


    public void print_debug(String message){
        Log.i("!!!!hekpper!!", message);
    }
}
