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
#"n","B","k","repe"
# inputFile outputFile identifierBenign identifierMalicious measurements
args <- commandArgs(trailingOnly = TRUE)
inputFile <- toString(args[1], width = NULL)
outputFile <- toString(args[2], width = NULL)
identifierBenign <- toString(args[3], width = NULL)
identifierMalicious <- toString(args[4], width = NULL)
measurements <- as.integer(args[5])
data <- read.csv(file = inputFile)
namedData <- data %>% mutate(V1 = recode(V1, "BASELINE" = "1"))  %>%  mutate(V1 = recode(V1, "MODIFIED" = "2"))

k <- 10
n <- measurements / k #c(10000,100000, 10000schritte)
B <- 1000
repe <- 100

#-------------------------------------------------------------------------------
#-------------------------------------------------------------------------------
autotest <- function(data, n, B, k, repe)
{
  #Daten ordnen
  bb1 <- data %>% select(V1,V2) %>% filter(V1=="1")
  bb2 <- data %>% select(V1,V2) %>% filter(V1=="2")
  #subsamples generieren
  subs1 <- list()
  subs2 <- list()
  for(i in 1:k){
    subs1[[i]] <- sample(bb1$V2,n,replace=TRUE)
    subs2[[i]] <- sample(bb2$V2,n,replace=TRUE)
  }
  q1 <- replicate(B,bootstrap1(n,subs1,k))
  q2 <- replicate(B,bootstrap1(n,subs2,k))
  maxq1 <- apply(q1 ,1, quantile , probs = 1 )
  maxq2 <- apply(q2 ,1, quantile , probs = 1 )
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
  return(dec)
}
#-------------------------------------------------------------------------------
#-------------------------------------------------------------------------------
test <- function(td1,td2){
  q1 <- hdquantile(td1, probs=seq(0.1,0.9,0.1), names=FALSE)
  q2 <- hdquantile(td2, probs=seq(0.1,0.9,0.1), names=FALSE)
  t1 <- abs(q1-q2)
  return(t1)
}
bootstrap1 <- function(n,dat,k)
{
  s <- sort(sample(seq(1,k, 1),2, replace=FALSE))
  x1 <- sample(dat[[s[1]]], n, replace=TRUE)
  x2 <- sample(dat[[s[2]]], n, replace=TRUE)
  q1 <- hdquantile(x1, probs=seq(0.1,0.9,0.1), names=FALSE)
  q2 <- hdquantile(x2, probs=seq(0.1,0.9,0.1), names=FALSE)
  test <- abs(q1-q2)
  return(test)
}
output <- autotest(namedData,n,B, k, repe)

f1a1 <- which((output/repe)[1,]<=0.01)
f1a2 <- which((output/repe)[2,]<=0.01)
#falls f1a1, f1a2 irgendwo >0.01 -> n erh?hen

signi <- which((output/repe)[3,]>=0.5)
#n erh?hen wenn 0.5 gr??er werden soll.
pos <- sum(signi%in%f1a1 & signi%in%f1a2)
save(output, file=outputFile)
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
