package com.example.readproducts

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.readproducts.ui.theme.ReadProductsTheme
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.generated.Uint16
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.io.IOException
import kotlin.ByteArray
import kotlin.CharArray
import kotlin.String
import org.web3j.abi.datatypes.Function as Function1001

class MainActivity : ComponentActivity() {
    private lateinit var nfcAdapter: NfcAdapter
    private val url = "http://192.168.33.78:8545"
    private val contractAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3"
    private val web3j = Web3j.build(HttpService(url))
    private lateinit var functionGetProduct: Function1001

    /**
     * Function launched on the execution of the Activity.
     * Initialize NFC Adapter.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            ReadProductsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,

                ) {
                    Greeting()
                }
            }
        }
    }

    /**
     * Function launched when the app change its stated to resumed.
     * Enable NFC Adapter for read.
     */
    override fun onResume() {
        super.onResume()
        nfcAdapter.enableReaderMode(
            this,
            { tag -> handleNfcTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F,
            null
        )
    }

    /**
     * Function launched when the user exits the application on the foreground and the app continues to run on background.
     * Disables foreground dispatch of nfcAdapter
     */
    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    /**
     * Function launched when a NFC tag has been read.
     * Contains the logic for reading MIFARE Classic smart cards.
     * At the end, if a successful read is given, function queryProduct is called.
     */
    private fun handleNfcTag(tag: Tag?) {
        val mfc = MifareClassic.get(tag)

        if (mfc != null && nfcAdapter.isEnabled) {
            try{
                mfc.connect()
                val authA = mfc.authenticateSectorWithKeyA(1,MifareClassic.KEY_DEFAULT)
                val authB = mfc.authenticateSectorWithKeyB(1,MifareClassic.KEY_DEFAULT)
                if (authA && authB) {
                    val block4Data = mfc.readBlock(4)
                    val block5Data = mfc.readBlock(5)
                    Log.v("NFC_TAG", "Datos del bloque 0 del sector 1: " + bytesToHexString(block4Data))
                    Log.v("NFC_TAG", "Datos del bloque 1 del sector 1: " + bytesToHexString(block5Data))
                    val blockData = ByteArray(20)
                    System.arraycopy(block4Data,0,blockData,0,block4Data.size)
                    System.arraycopy(block5Data,0,blockData,block4Data.size,4)
                    val productAddress = "0x" + bytesToHexString(blockData)
                    if(productAddress != "0x0000000000000000000000000000000000000000"){
                        Log.v("NFC_TAG", "Product Address: " + bytesToHexString(blockData))
                        Log.v("NFC_TAG", "Product Address: $productAddress")
                        runOnUiThread {
                            Toast.makeText(this, "Product Address: $productAddress",Toast.LENGTH_SHORT).show()
                        }
                        queryProduct(productAddress)
                    }else{
                        runOnUiThread {
                            Toast.makeText(this, "Void address read",Toast.LENGTH_SHORT).show()
                        }
                    }
                }else{
                    runOnUiThread {
                        Toast.makeText(this, "Wrong authentication",Toast.LENGTH_SHORT).show()
                    }
                }
            }catch(e: IOException){
                e.printStackTrace()
            }finally {
                try {
                    mfc.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }else{
            runOnUiThread {
                Toast.makeText(this, "Smart card read with unexpected format",Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Function used for converting byte array to an hexadecimal string.
     */
    private fun bytesToHexString(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Function launched when a successful Mifare Classic smart card has occurred.
     * It contains the logic to ask product information to the chain and the IPFS service in order to display it.
     * At the end, if the query of the product has been satisfactory, it displays the product information gathered.
     */
    private fun queryProduct(productAddress: String){
        val outputParams = listOf(
            object : TypeReference<Address>() {},
            object : TypeReference<Uint8>() {},
            object : TypeReference<Uint16>() {},
            object : TypeReference<org.web3j.abi.datatypes.Utf8String>() {},
            object : TypeReference<org.web3j.abi.datatypes.Utf8String>() {},
            object : TypeReference<Uint8>() {}
        )

        functionGetProduct = Function1001(
            "getProduct",
            listOf(Address(productAddress)),
            outputParams
        )

        val functionData = FunctionEncoder.encode(functionGetProduct)

        val result = web3j.ethCall(
            org.web3j.protocol.core.methods.request.Transaction
                .createEthCallTransaction(null, contractAddress, functionData),
            org.web3j.protocol.core.DefaultBlockParameterName.LATEST
        ).sendAsync().get()

        val decodedResult = FunctionReturnDecoder.decode(result.value, functionGetProduct.outputParameters)

        // Prepare values for further printing
        val owner = decodedResult[0].value.toString()
        var productType = ""
        when(decodedResult[1].value.toString()){
            "0" -> productType = "Plastic Bottle"
            "1" -> productType = "Can"
            "2" -> productType = "Glass Bottle"
            "3" -> productType = "Cardboard"
        }
        decodedResult[1].value.toString()
        val timesRecycled = decodedResult[2].value.toString()
        val cid = decodedResult[3].value.toString().replaceFirst(Regex("^ipfs://"), "")
        val productName = decodedResult[4].value.toString()
        var state = ""
        when(decodedResult[5].value.toString()){
            "0" -> state = "Usable"
            "1" -> state = "Pending to recycle"
        }
        val imageURL = "https://$cid.ipfs.nftstorage.link/$productName.jpg"

        setContent {
            ReadProductsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PrintProduct(
                        productAddress = productAddress,
                        ownerAddress = owner,
                        productType = productType,
                        timesRecycled = timesRecycled,
                        cid = cid,
                        productName = productName,
                        state = state,
                        imageURL = imageURL
                    )
                }
            }
        }
    }
}

/**
 * View of the product to be printed.
 * This function is called in order to print the product information, given the parameters detailed on the input of the function.
 */
@Composable
fun PrintProduct(ownerAddress: String,
             productAddress: String,
             productType: String,
             timesRecycled: String,
             cid: String,
             productName: String,
             state: String,
             imageURL: String,
             modifier: Modifier = Modifier) {
    val customTextStyle = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    )
    val bodyTextStyle = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    )

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Product found!", style = customTextStyle)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Product Address: $productAddress", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Owner: $ownerAddress", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Product Type: $productType", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Times Recycled: $timesRecycled times", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "CID: $cid", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Product Name: $productName", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "State: $state", style = bodyTextStyle)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "Product Image:", style = bodyTextStyle)

        Image(
            painter = rememberAsyncImagePainter(imageURL),
            contentDescription = null,
            modifier = Modifier.size(200.dp)
        )
    }
}

/**
 * Startup view, called by default.
 */
@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Waiting product",
        modifier = modifier
    )
}