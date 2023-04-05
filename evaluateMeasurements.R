#library

library(robustHD)
library(mixdist)
library(tidyverse)
#library(WRS)
library(WRS2)
library(Hmisc)
library(parallel)


#-------------------------------------------------------------------------------
#------------------------------------------------------------------------------
# Args: inputFile outputFile identifierBenign identifierMalicious measurements
args <- commandArgs(trailingOnly = TRUE)
inputFile <- toString(args[1], width = NULL)
outputFile <- toString(args[2], width = NULL)
identifierBenign <- toString(args[3], width = NULL) # ignored & hardcoded
identifierMalicious <- toString(args[4], width = NULL) # ignored & hardcoded
measurements <- as.integer(args[5])
data <- read.csv(file = inputFile)
namedData <- data %>% mutate(V1 = recode(V1, "BASELINE" = "1"))  %>%  mutate(V1 = recode(V1, "MODIFIED" = "2"))

k <- 10
n <- measurements
m <- n/k #c(10000,100000, 10000schritte)
B <- 10000
repe <- 1000

#-------------------------------------------------------------------------------
#-------------------------------------------------------------------------------
autotest <- function(data, n, B, repe,m)
{
  #Daten ordnen
  bb1 <- data %>% select(V1,V2) %>% filter(V1=="1")
  bb2 <- data %>% select(V1,V2) %>% filter(V1=="2")
  #Änderung1
  q1 <- replicate(B,bootstrap1(as.numeric(bb1$V2),m))
  q2 <- replicate(B,bootstrap1(as.numeric(bb2$V2),m))
  maxq1 <- apply(q1 ,1, quantile , probs = 0.99 )
  maxq2 <- apply(q2 ,1, quantile , probs = 0.99 )
  maxqs <- matrix(c(maxq1,maxq2), nrow=9, ncol=2)
  qmax <- apply(maxqs,1 ,max)
  
  b <- list(bb1, bb2)
  t <- list()
  dec <- matrix(rep(0, 3*9), nrow=3, ncol=9)
  for(i in 1:2){
    for(j in 1:repe){
    eins <- sample(b[[i]]$V2, n, replace=TRUE)
    zwei <- sample(b[[i]]$V2, n, replace=TRUE)
    t[[j]] <- test(eins, zwei)
    for(l in 1:9){
      if(t[[j]][l]>qmax[l]){
        dec[i,l] <-dec[i,l]+1 
        
      }
    }
    }
  }
  for(j in 1:repe){
    eins <- sample(b[[1]]$V2, n, replace=TRUE)
    zwei <- sample(b[[2]]$V2, n, replace=TRUE)
    t[[j]] <- test(eins, zwei)
    for(l in 1:9){
      if(t[[j]][l]>qmax[l]){
        dec[3,l] <-dec[3,l]+1 
        
      }
    }
  }
  return(cbind(dec,matrix(c(qmax[1],qmax[2],qmax[3],qmax[4],qmax[5],qmax[6],qmax[7],qmax[8],qmax[9]),ncol=3,nrow=3)))
}
#-------------------------------------------------------------------------------
#-------------------------------------------------------------------------------
test <- function(td1,td2){
  q1 <- hdquantile(td1, probs=seq(0.1,0.9,0.1), names=FALSE)
  q2 <- hdquantile(td2, probs=seq(0.1,0.9,0.1), names=FALSE)
  t1 <- abs(q1-q2)
  return(t1)
}
bootstrap1 <- function(dat,m)
{
  x1 <- sample(dat, m, replace=TRUE)
  x2 <- sample(dat, m, replace=TRUE)
  q1 <- hdquantile(x1, probs=seq(0.1,0.9,0.1), names=FALSE)
  q2 <- hdquantile(x2, probs=seq(0.1,0.9,0.1), names=FALSE)
  test <- abs(q1-q2)
  return(test)
}
output <- autotest(namedData,n,B, repe,m)

f1a1 <- which((output/repe)[1,]<=0.02)
f1a2 <- which((output/repe)[2,]<=0.02)


signi <- which((output/repe)[3,]>=0.5)
#n erh?hen wenn 0.5 gr??er werden soll.
pos <- sum(signi%in%f1a1 & signi%in%f1a2)
save(output, file=outputFile)
linesF1a <- c(output[1,1], output[1,2], output[1,3], output[1,4], output[1,5], output[1,6], output[1,7], output[1,8], output[1,9], output[2,1], output[2,2], output[2,3], output[2,4], output[2,5], output[2,6], output[2,7], output[2,8], output[2,9])
line3 <- c(output[3,1], output[3,2], output[3,3], output[3,4], output[3,5], output[3,6], output[3,7], output[3,8], output[3,9])
linesDecision <- c(output[1,10], output[2,10], output[3,10], output[1,11], output[2,11], output[3,11] , output[1,12], output[2,12], output[3,12])
maxF1a <- max(linesF1a)
maxPower <- max(line3)
biggestDifferenceIndex <- which.max(linesDecision)
additionalOutput <- c(maxF1a, maxPower, biggestDifferenceIndex)

additionalString = paste(c("Additional", additionalOutput), collapse = ",")
additionalFile = paste(outputFile, ".add")
additionalFile = gsub(" ", "", additionalFile)
cat(additionalString,file=additionalFile,append=FALSE)

print(output)
if(length(f1a1)>=9&&length(f1a2)>=9){
if(pos>=1){
  print(signi)
  quit(status=12)
}
if(pos<1){
  print("No difference yet")
  quit(status=14)
}
}else{
  print("F1A nicht korrekt")
  quit(status=13)
}
