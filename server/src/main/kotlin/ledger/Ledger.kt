package ledger
import com.rest_api.types.Tx
import com.rest_api.types.UTxO

class Ledger {
    // <address, list of transactions>
    val utxos: HashMap<String, List<UTxO>> = HashMap<String, List<UTxO>> ()
    val txs: HashMap<String, List<Tx>> = HashMap<String, List<Tx>> ()


}