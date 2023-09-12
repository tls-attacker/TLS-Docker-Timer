# Check if the user provided two command line arguments
args <- commandArgs(trailingOnly = TRUE)
if (length(args) != 2) {
  cat("Length was ", length(commandArgs()))
  stop("Usage: Rscript getDecisionDetails.R <input_file> <output_file>")
}

# Read in the first command line argument as the input file path
input_file <- args[1]

# Load the RDATA file into the variable 'loaded'
load(args[1], loaded <- new.env())

# Create a vector of numbers 1 through 4
testStats <- "St.:"
decisionRules <- "Dc.:"


diffVector <- loaded[["output"]][[2]] - loaded[["output"]][[3]]
maxVal <- max(diffVector) / 3.4

# Open a connection to a file and write the string
file_conn <- file(args[2], "w")
#writeLines(paste(loaded[["output"]][[2]], loaded[["output"]][[3]], diffVector, quotient_vec, maxVal, sep = " # "), file_conn)
writeLines(as.character(maxVal), file_conn)
close(file_conn)

