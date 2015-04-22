package info.blockchain.wallet.payload;

import android.util.Log;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.Base58;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.params.MainNetParams;

import org.apache.commons.lang.ArrayUtils;
import org.json.simple.JSONObject;

import java.math.BigInteger;

import info.blockchain.wallet.util.DoubleEncryptionFactory;

public class LegacyAddress {

    private String strEncryptedKey = null;
    private long created = 0L;
    private String strAddress = null;
    private String strLabel = null;
    private String strCreatedDeviceName = null;
    private String strCreatedDeviceVersion = null;
    private long tag = 0L;

    public LegacyAddress() { ; }

    public LegacyAddress(String encryptedKey, long created, String address, String label, long tag, String device_name, String device_version) {
        this.strEncryptedKey = encryptedKey;
        this.created = created;
        this.strAddress = address;
        this.strLabel = label;
        this.tag = tag;
        this.strCreatedDeviceName = device_name;
        this.strCreatedDeviceVersion = device_version;
    }

    public LegacyAddress(String encryptedKey, String address) {
        this.strEncryptedKey = encryptedKey;
        this.created = 0L;
        this.strAddress = address;
        this.strLabel = "";
        this.tag = 0L;
    }

    public String getEncryptedKey() {
        return strEncryptedKey;
    }

    public void setEncryptedKey(String encryptedKey) {
        strEncryptedKey = encryptedKey;
    }
    
    public void setEncryptedKey(byte[] privKeyBytes) {
    	strEncryptedKey = new String(Base58.encode(privKeyBytes));
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getAddress() {
        return strAddress;
    }

    public void setAddress(String address) {
        strAddress = address;
    }

    public String getLabel() {
        return strLabel;
    }

    public void setLabel(String label) {
        strLabel = label;
    }

    public long getTag() {
        return tag;
    }

    public void setTag(long tag) {
        this.tag = tag;
    }

    public String getCreatedDeviceName() { return strCreatedDeviceName; }

    public void setCreatedDeviceName(String device_name) { this.strCreatedDeviceName = device_name; }

    public String getCreatedDeviceVersion() { return strCreatedDeviceVersion; }

    public void setCreatedDeviceVersion(String device_version) { this.strCreatedDeviceVersion = device_version; }

    public String getPrivateKey() throws AddressFormatException {

        ECKey ecKey = getECKey();

        if(ecKey != null) {
            return ecKey.getPrivateKeyEncoded(MainNetParams.get()).toString();
        }
        else {
            return null;
        }
    }
    
    public ECKey getECKey() throws AddressFormatException {

    	byte[] privBytes = null;

    	if(!PayloadFactory.getInstance().getPayloadObject().isDoubleEncrypted()) {
        	privBytes = Base58.decode(this.strEncryptedKey);
    	}
    	else {
    		/*
    		Log.i("LegacyAddress double encrypted", strEncryptedKey);
    		Log.i("LegacyAddress double encrypted", PayloadFactory.getInstance().getPayloadObject().getSharedKey());
    		Log.i("LegacyAddress double encrypted", PayloadFactory.getInstance().getTempDoubleEncryptPassword().toString());
    		Log.i("LegacyAddress double encrypted", "hash:" + DoubleEncryptionFactory.getInstance().validateSecondPassword(PayloadFactory.getInstance().getPayloadObject().getDoublePasswordHash(), PayloadFactory.getInstance().getPayloadObject().getSharedKey(), PayloadFactory.getInstance().getTempDoubleEncryptPassword(), PayloadFactory.getInstance().getPayloadObject().getIterations()));
    		Log.i("LegacyAddress double encrypted", PayloadFactory.getInstance().getPayloadObject().getDoublePasswordHash());
    		Log.i("LegacyAddress double encrypted", "" + PayloadFactory.getInstance().getPayloadObject().getIterations());
    		*/
    		String encryptedKey = DoubleEncryptionFactory.getInstance().decrypt(strEncryptedKey, PayloadFactory.getInstance().getPayloadObject().getSharedKey(), PayloadFactory.getInstance().getTempDoubleEncryptPassword().toString(), PayloadFactory.getInstance().getPayloadObject().getIterations());
//    		Log.i("LegacyAddress double encrypted", encryptedKey);
        	privBytes = Base58.decode(encryptedKey);
    	}

    	ECKey ecKey = null;

    	ECKey keyCompressed = null;
		ECKey keyUnCompressed = null;
		BigInteger priv = new BigInteger(privBytes);
		if(priv.compareTo(BigInteger.ZERO) >= 0) {
			keyCompressed = new ECKey(priv, null, true);
			keyUnCompressed = new ECKey(priv, null, false);
		}
		else {
			byte[] appendZeroByte = ArrayUtils.addAll(new byte[1], privBytes);
			BigInteger priv2 = new BigInteger(appendZeroByte);
			keyCompressed = new ECKey(priv2, null, true);			
			keyUnCompressed = new ECKey(priv2, null, false);
		}

		if(keyCompressed != null && keyCompressed.toAddress(MainNetParams.get()).toString().equals(this.strAddress)) {
			Log.i("Using ECKey", "compressed");
			ecKey = keyCompressed;
		}
		else if(keyUnCompressed != null && keyUnCompressed.toAddress(MainNetParams.get()).toString().equals(this.strAddress)) {
			Log.i("Using ECKey", "uncompressed");
			ecKey = keyUnCompressed;
		}
		else {
			ecKey = null;
			Log.i("ECKey error", "cannot process legacy private key");
		}

		return ecKey;
    }

    public JSONObject dumpJSON() {

        JSONObject obj = new JSONObject();

        /*
        try {
            obj.put("priv", getPrivateKey());
        }
        catch(AddressFormatException afe) {
            ;
        }
        */

        obj.put("priv", strEncryptedKey);
        obj.put("created_time", created);
        obj.put("addr", strAddress);
        obj.put("label", strLabel == null ? "" : strLabel);
        obj.put("tag", tag);
        obj.put("created_device_version", strCreatedDeviceVersion);
        obj.put("created_device_name", strCreatedDeviceName);

        return obj;
    }

}
