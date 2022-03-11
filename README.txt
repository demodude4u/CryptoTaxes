/////////////////////////////////////////////////////////////////
*                                                               *
*               Demod's Crypto Tax Program v0.1                 *
*	                                                        *
*   I provide this software AS IS, without warranty or any      *
*   guarantees that it works as intended.  Double check your    *
*   tax files and make sure everything makes sense!             *
*                                                               *
*       Released for UNDRGRND community <3                      *
*                                                               *
*       Github https://github.com/demodude4u/CryptoTaxes        *
*                                                               *
*       This program is free, but maybe consider buying me      *
*       some soda to keep me going!  My wallet is demod.eth     *
*                                                               *
/////////////////////////////////////////////////////////////////

*****************************************************************
*** LIMITATIONS *************************************************

I mainly made this program for my own needs, so I don't have full
support for all EVM blockchains.  I may be able to extend support
to other blockchains, as long as the block explorer is easy to 
scrape for data.  Message me and I'll take a look into it.

Currently supported:
 - Ethereum
 - BSC
 - Polygon
 - Avalanche
 - KCC

Even if your blockchain isn't currently supported, you can still 
manually create your own input files and skip steps 1 through 3.

You will need to modify your exports from exchanges to match the
same input file format.  Message me and I can help with the 
exchanges that I also use.

*****************************************************************
*** GETTING STARTED *********************************************

#1  Download and install java (version 15 or higher):
https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.2%2B8/OpenJDK17U-jdk_x64_windows_hotspot_17.0.2_8.msi

#2  In the data folder, edit the config.json file:
 - Your wallets
 - Known stablecoins
 - Exclusions
 - Symbol Renaming

#3  There are 5 batch files, one for each script.  Make sure you 
read the documentation and understand what input files are needed 
for each script.

*****************************************************************
*** SCRIPT 1 - FIND TRANSACTIONS (OPTIONAL) *********************

I highly recommend skipping this script, if possible.  It will 
take a long time to run.

This script will search for every single transaction in the tax
year and save your wallet(s) tx hashes into transactions.csv

It will also find any transactions that mention your wallet(s),
such as airdrops or MEV bots.

##  Script input files:
 - None

##  Script output file:
 - \reports\(year)\(chain)\transactions.csv

This is the only script that can be interrupted and resumed 
later.  Press enter while the script is running and it will
find the next chance it can pause and save the progress in
the data.json file.

Once the script is complete, the transactions.csv file will
contain each transaction hash found.

If you want to skip this step, go to the block explorer and
manually create the transactions.csv file, as it will be needed 
for script 2.  Many explorers have the option to export data, 
all you will need is the transaction hashes.

The format for transactions.csv is very simple.  It is just
a list of hashes, separated per line, no header. For example:

+------- \reports\2021\Ethereum\transactions.csv (example) ----------+
| 0xab74b85fe4a49a91dcb81925be7f40ad6c256300395893348a6b08ab893a7b04 |
| 0x25ed1c346eb77a8104e9e6200809f8abee5d6a87c12c368146e71bc69846c9a1 |
| 0xf918e81e43e60ff91ddc3c8cb65d0605735251618116916687ec81b5002e353c |
| 0xcb346bf34d685eef5a086defce8a9cd1ac4ad042a2c5504aadb00f6d421b272d |
| 0x2ca69a64003b808d0059b6e628f89e651926553222b2f8f39ffb96adf5459585 |
| 0xcdf483ebed6e819c69d33e5394dd92abfacf2aa4928f3573eb00343197fadc59 |
| 0x5c5b4f25d4e33aae2006388af9b5cf4b62853a2ccb30ee9f427db3e4dd41e54e |
+--------------------------------------------------------------------+

*****************************************************************
*** SCRIPT 2 - SCRAPE TOKENS (OPTIONAL) *************************

This script will take the transaction hashes from script 1, 
and query the block explorer for token transfer information.

##  Script input file:
 - \reports\(year)\(chain)\transactions.csv

##  Script output file:
 - \reports\(year)\(chain)\data.json

Custom code had to be written for each block explorer...
The parsing logic is not perfect and may fail on you. Please 
message me about any failure and copy the error information.

Some block explorers don't like my program, and may try to 
block you.  Try again with a VPN if this happens.

Once the script is complete, the data.json file will be full
of data scraped from the block explorer.

*****************************************************************
*** SCRIPT 3 - IDENTIFY EVENTS (OPTIONAL) ***********************

This script will load the data.json file and try to indentify 
buying, selling, and swapping events.

##  Script input file:
 - \reports\(year)\(chain)\data.json

##  Script output file:
 - \reports\(year)\(chain)\(year)_(chain)_taxevents.csv

Price history will be queried from CoinGecko, but my program
is limited to only one query per second, so it may be slow.

For any tokens that cannot determine which price to use from
CoinGecko, edit \data\coingecko-symbol-pref.json and add the
symbol there with the id that CoinGecko uses.  The id is called
"API id" on CoinGecko's website, which can be found in the info
pane along the right side.  If you choose to ignore the price 
set on CoinGecko (or it doesn't have your shitcoin), set the id 
to null.

Once the script is done, manually inspect the taxevents.csv file
and verify the prices appear correct.  CoinGecko does not always 
pick the right price for the right token, especially for those 
scam airdrops that everyone gets.  You can just delete those rows
if necessary.

*****************************************************************
*** SCRIPT 4 - GENERATE TAX LOG *********************************

Before running this script, make sure you have gathered all tax
event files into the \data\2021 folder (create the folder if it 
is missing), or for whatever tax year you are currently doing.

For every blockchain you ran previous scripts on, copy the 
(year)_(chain)_taxevents.csv file into the \data\(year) folder 
and manually verify the data appears to be correct.

For any exchanges you used, convert the export files into the 
same format found in the tax events files (including the header):

+------- \data\2021\example_taxevents.csv (example) -----------------------------------------------------------------+
|Date                ,Account  ,Event    ,Asset   ,Amount      ,Value          ,TransactionID                        |
|1/1/2021 21:05:36   ,Coinbase ,REWARD   ,ALGO    ,0.000037    ,0.00001509415  ,82fd6a4b-a12c-51a1-90b0-7c65e1b41ab0 |
|1/2/2021 22:10:11   ,Coinbase ,REWARD   ,ALGO    ,0.000037    ,0.00001486475  ,53991f2c-17f2-5949-a476-d1efa4e353f6 |
|1/3/2021 6:33:52    ,Coinbase ,BUY      ,ETH     ,1           ,811.82         ,889ca399-a7a6-543b-ae23-7b74fb06a211 |
|1/3/2021 8:53:20    ,Coinbase ,WITHDRAW ,ETH     ,0.50189     ,407.4443398    ,20b277d0-f81b-5fe8-9650-bfb4bf0cbabd |
|1/3/2021 8:54:18    ,Coinbase ,WITHDRAW ,ETH     ,0.49811     ,404.3756602    ,47fffcce-0f3b-55d4-a725-9ab6661ee5f1 |
|1/3/2021 21:08:30   ,Coinbase ,REWARD   ,ALGO    ,0.000037    ,0.00001520885  ,a45fc1cf-dab2-51a8-ac80-f2b98e285e07 |
|1/4/2021 7:33:49    ,Coinbase ,SELL     ,UNI     ,101.3555862 ,566            ,d0d1cf54-1c5b-5efd-8dca-dc7dfafc967a |
+--------------------------------------------------------------------------------------------------------------------+

The Event column can be one of the following:
 - BUY (Value is cost basis)
 - SELL (Value is proceeds, minus fees)
 - DEPOSIT (Ignored by the program)
 - WITHDRAW (Ignored by the program)
 - FEE (Value is ignored, amount is removed from lot but cost basis remains the same)
 - REWARD (Value is income, if program is set to apply as income)
 - UNKNOWN (Ignored by the program)
 - REMOVED (Same as FEE)
 - CARRYOVER (Unsold lots from previous tax years, same as BUY)

The Value column can be empty if the cost/proceeds are not 
known.  The program will query CoinGecko for the price and value.

The TransactionID column is used to help reorder transactions, 
but not required.  It can also be used to trace back to the 
original data.

Any extra columns included in the tax events files will be 
ignored.  Any whitespace padding will also be ignored.

If you ran this program for previous year's taxes, also include 
a copy of the (year)_(algo)_carryover.csv file in the \data\(year)
folder.  This is important for finding any long-term gains.  If 
you used another tax program for the previous year, identify the 
remaining lots and re-classify them as CARRYOVER events.

##  Script input files:
 - All .csv files in \data\(year) folder
    (must match tax events file format)

##  Script output files:
 - \reports\(year)\taxes\(algo)_carryover.csv
 - \reports\(year)\taxes\(algo)_log.csv
 - \reports\(year)\taxes\(algo)_monthly.csv
 - \reports\(year)\taxes\(algo)_summary.csv

After running this script, double check everything makes sense 
in the output files.  If some values don't add up, double-check 
the input files are correct and verify CoinGecko is providing 
the correct prices/values.

This script also runs script 5 automatically, to make sure the 
output files add up correctly.  If you are happy with the results 
from this script, you do not need to run script 5.

I encourage you to run every lot algorithm, to find the best 
outcome that suits your tax needs.

******************************************
*** SCRIPT 5 - VERIFY TAXES (OPTIONAL) ***

Script 4 automatically runs this script, you do not need to run 
this script if you are satisfied with the results from script 4.

If you choose to modify any data in (year)_(algo)_log.csv, this 
script will verify the edits are valid and re-generate the 
carryover, monthly, and summary output files to reflect the 
changes made.

##  Script input file:
 - \reports\(year)\taxes\(algo)_log.csv

##  Script output files:
 - \reports\(year)\taxes\(algo)_carryover.csv
 - \reports\(year)\taxes\(algo)_monthly.csv
 - \reports\(year)\taxes\(algo)_summary.csv

*****************************************************************
*****************************************************************

Let me know if you have any issues, I'll try to help if I can!
 - Demod
