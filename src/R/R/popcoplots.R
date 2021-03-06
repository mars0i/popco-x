# popcoplots.R

# First CREATE 4-D MULTIRUN ARRAY FROM LIST/ARRAY OF CSV FILENAMES:
# mra <- read2multirunRA(csvs) 

##########################################
# tools for debugging panel functions, etc.

# panel.assign2panelArgs
# assigns whatever is passed to the panel function to 
# top level variable panelArgs, with one list of arguments
# for every panel.
# USAGE: *first* define panelArgs as a list at the top level:
# PanelArgs <- list()
# if you don't do that, this function will have no effect.
panel.assign2panelArgs <- function(...){
  panelArgs[[panel.number()]] <<- list(...)
}

panel.displayLayoutInfo <- function(...) {
  cat("row:", current.row(...), "col:", current.column(...), "panel:", panel.number(...), "packet:", packet.number(...), "which.packet:", which.packet(...), "\n")
}

# example:
# bwplot(CV + CB ~ bias, data=socnet5with300runst5000.df[socnet5with300runst5000.df$rawsum=="raw",],
#                        ylim=c(-1,1), horizontal=F, pch="|", coef=0, outer=T,
#                        par.settings=list(box.rectangle=list(col="black"),
#                        box.umbrella=list(col="black")),
#                        panel=panel.setPanelArgs)
# produces:
# > str(PanelArgs)
# List of 2
#  $ :List of 6
#   ..$ x         : Factor w/ 2 levels "beast","virus": 1 1 1 1 1 1 1 1 1 1 ...
#   ..$ y         : num [1:600] 0.207 0.2064 0.0692 0.208 -0.0698 ...
#   ..$ pch       : chr "|"
#   ..$ coef      : num 0
#   ..$ box.ratio : num 1
#   ..$ horizontal: logi FALSE
#  $ :List of 6
#   ..$ x         : Factor w/ 2 levels "beast","virus": 1 1 1 1 1 1 1 1 1 1 ...
#   ..$ y         : num [1:600] 0.000133 -0.001456 -0.15072 -0.149572 0.000551 ...
#   ..$ pch       : chr "|"
#   ..$ coef      : num 0
#   ..$ box.ratio : num 1
#   ..$ horizontal: logi FALSE
# >
# Note that in both top-level list elements, x is the same, and contains all 600 "beast"/"virus" factor entries.
# y differs: The first list contains the 600 CV values, and the second contains the 600 CB values.
# Each list corresponds to a distinct call to the panel function.
# A bwplot or violin plot using this form will normally create two panels, one for CV and the other for CB,
# i.e. one for each call to the panel function, I believe.  In each panel, there will be two figures,
# corresponding to the two bias factors.
# 

# make a centered "Tower of Hanoi" histogram
# lattice version
# based on Greg Snow's base graphics version in response to my question at:
# http://stackoverflow.com/questions/15846873/symmetrical-violin-plot-like-histogram
# default behavior of breaks is hist()'s rather than histograms()'s default
panel.hanoi <- function(x, y, horizontal, breaks="Sturges", ...) {  # "Sturges" is hist()'s default

  if (horizontal) {
    condvar <- y # conditioning ("independent") variable
    datavar <- x # data ("dependent") variable
  } else {
    condvar <- x
    datavar <- y
  }

  conds <- sort(unique(condvar))

  # loop through the possible values of the conditioning variable
  for (i in seq_along(conds)) {

      h <- hist(datavar[condvar == conds[i]], plot=F, breaks) # use base hist(ogram) function to extract some information

    # strip outer counts == 0, and corresponding bins
    brks.cnts <- stripOuterZeros(h$breaks, h$counts)
    brks <- brks.cnts[[1]]
    cnts <- brks.cnts[[2]]

    halfrelfs <- (cnts/sum(cnts))/2  # i.e. half of the relative frequency
    center <- i

    # All of the variables passed to panel.rec will usually be vectors, and panel.rect will therefore make multiple rectangles.
    if (horizontal) {
      panel.rect(butlast(brks), center - halfrelfs, butfirst(brks), center + halfrelfs, ...)
    } else {
      panel.rect(center - halfrelfs, butlast(brks), center + halfrelfs, butfirst(brks), ...)
    }
  }
}


# This function will generate an error if cnts contains all zeros.  However, 
# this should not happen in panel.hanoi.  It would mean that you have no data
# and you'd get an error before getting to the call to this function.
stripOuterZeros <- function(brks, cnts) { do.call("stripLeftZeros", stripRightZeros(brks, cnts)) }

stripLeftZeros <- function(brks, cnts) {
  if (cnts[1] == 0) {
    stripLeftZeros(brks[-1], cnts[-1])
  } else {
    list(brks, cnts)
  }
}

stripRightZeros <- function(brks, cnts) {
  len <- length(cnts)
  if (cnts[len] ==0) {
    stripRightZeros(brks[-(len+1)], cnts[-len])
  } else {
    list(brks, cnts)
  }
}

##########################################

# activnsAtTickBarchart:
# Return barcharts of activations for each proposition grouped by person
# using 4-D array mra of popco data
# at tick
# this version assumes 4 domains
activnsAtTickBarchart <- function(mra, tick, run=1, main=paste("tick", tick), xlab="activation", cex=.5) {
  require(lattice)

  divadj <- .50 # pushes inter-domain lines up a bit
  # trellgray <- trellis.par.get("reference.line")$col;  # gets default grid gray - lighter than "gray"

  # extract propositions and domain info from data array, and prepare param lists for barchart:
  npersons <- dim(mra)[1]
  propnms <- dimnames(mra)$proposition
  domnms <- genPropNames2domNames(propnms) # get a vector of all domains to which these propns are assigned
  domsizes <- unlist(lapply(domnms, countPropsInDomain, propnms=propnms))  # count how many propns in each domain
  domdivs <- cumsum(domsizes[-length(domsizes)])+divadj  # cumulative sums excluding last element
  domcols <- c(rep("blue", domsizes[1]), rep("darkgreen", domsizes[2]), rep("red", domsizes[3]), rep("darkorange", domsizes[4]))

  barchart(t(mra[,,tick,run]), groups=person, 
           xlim=c(-1,1), 
           scales=list(cex=cex, y = list(alternating = 3)), 
	   xlab=xlab,
	   layout=c(npersons,1),
	   main=main,
           panel = function(y, ...){
             panel.abline(v=c(-.5,.5), lty=3, col="gray");
             panel.abline(h=domdivs, lty=2, col="gray");
             panel.barchart(y=y, col=domcols[y], border="transparent", ...)})
}

# plot 2D plot of activation means from different runs
# form: a formula
# data: a dataframe
# yfoci, xfoci: vectors of values at which means are expected to lie [in order y, x, since that's order in formula]
# anonymous fifth argument can be groups=columns to distinguish different data within each plot
xyMeanActivnPlot <- function(form, data, yfoci=seq(-1,1,.2), xfoci=seq(-1,1,.2), auto.key=T, ...){
  require(lattice)
  xyplot(form, data=data, xlim=c(-1,1), ylim=c(-1,1), aspect="iso", auto.key=auto.key,
         abline=c(list(h=yfoci, v=xfoci),             # grid
		  list(a=0,b=1),                      # diagonal
                  trellis.par.get("reference.line")), # color
         ...)  # groups argument might be passed here
}

# tips:
# How to control font size in the panel label bars:
# xyMeanActivnPlot(CV~CB|desc, df[df$model!="crime3socnet2",], yfoci=cv.foci, xfoci=cb.foci, par.strip.text=list(cex=.85))


addJitter <- function(trellobj=trellis.last.object(), amount=.025) {update(trellobj, jitter.x=T, jitter.y=T, amount=amount)}

# make a pdf with xyMeanActivn plots from two dataframes in it
xyMeanActivnPlotsBiasPDF <- function(df1, df2, domy, domx, ...) {
  unconditioned.string <- paste0(domy, "~", domx)
  unconditioned.form <- as.formula(unconditioned.string)
  conditioned.string <- paste0(unconditioned.string, "|bias")
  conditioned.form <- as.formula(conditioned.string)

  df1.main.plot <- xyMeanActivnPlot(conditioned.form, groups=rawsum, data=df1, ...)
  df1.jitter.plot <- addJitter(trellobj=df1.main.plot)
  df1.mean.plot <- xyMeanActivnPlot(unconditioned.form, groups=bias, data=df1[df1$rawsum=="mean",], ...)

  df2.main.plot <- xyMeanActivnPlot(conditioned.form, groups=rawsum, data=df2, ...)
  df2.jitter.plot <- addJitter(trellobj=df2.main.plot)
  df2.mean.plot <- xyMeanActivnPlot(unconditioned.form, groups=bias, data=df2[df2$rawsum=="mean",], ...)

  pdf()
  print(df1.main.plot)
  print(df1.jitter.plot)
  print(df1.mean.plot)
  print(df2.main.plot)
  print(df2.jitter.plot)
  print(df2.mean.plot)
  dev.off()
}
# Set background color of the top label strips in each panel.
# Note that this modifies how the trellis object is displayed; it doesn't modify the object itself, apprently.
# e.g. to make it affect a PDF file, call pdf(), then call this, then run the plot, then dev.off().
stripbg <- function(colorstring){
  sb <- trellis.par.get("strip.background") 
  sb[["col"]][1] <- colorstring
  trellis.par.set("strip.background", sb)
}

# Plot max and min differences between all possible pairs of runs, summarizing how they diverge (or don't)
# parameters:
# mra:		a popco multi-run array. e.g. mra[,,,4:6] will plot differences between runs with indexes 4, 5, and 6.
# ymin, ymin:	min and max y-values allowed in the plot. e.g. make these small to focus in on micro-variation.
# type:		whether to plot smooth line, points, etc. used as value of parameter 'type' in line().
# pch:		for type="p", determines what symbols are plotted; also passed to line().
# jitter: 	if non-zero, a random number from a Gaussian distribution are added to each line.  This allow seeing
#         	overlapping lines.  The Gaussian dist has mean 0, and standard deviation equal to the value of jitter.
#		jitter = .01 or .02 is probably a good choice.
# Note: Uses regular R graphics, not Lattice or ggplot2.
plotPairwiseDiffs <- function(mra, ymax=2, ymin=-2, type="l", pch=".", jitter=0) {
	# list of possible plotting colors:
	plotcolors <- c("black", "blue", "red", "purple", "green", "darkorange", "darkgreen", "navyblue")

	# these will be used to construct a color key label:
	colorsep <- "   "  # this will separate specific color key strings
	colorkey <- ""     # we'll add text to this inside the loop below

	numruns <- dim(mra)[4]
	numticks <- dim(mra)[3]

	# set up plot window:
	plot(0, type="l", 
		xlim=c(1, numticks),
		ylim=c(ymin, ymax),	# max poss variation is 1 minus -1
		xlab=paste(sub(".*/", "", dimnames(mra)$run), collapse=", "), # if run names have paths included, strip them off
		main="max and min differences between runs")

	abline(h=0, col="gray", lty=3)  # add reference line at y=0. lty=2 for dashed, 3 for dotted

	runpairs <- expand.grid(a=1:numruns, b=1:numruns) # get all ordered pairs of run indexes
	runpairs <- runpairs[runpairs$a > runpairs$b, ]   # strip unordered duplicate rows and rows with two of the same index

	for (i in 1:dim(runpairs)[1]) {
		runA <- runpairs[i,1]
		runB <- runpairs[i,2]

		diffs1 <- mra[,,,runA]-mra[,,,runB]
		diffs2 <- mra[,,,runB]-mra[,,,runA]

		# Make sure that the upper plot goes farther up than the lower one goes down.
		# That way, similar plots in different loop iterations will get overlaid instead of appearing to be mirrored.
		if (max(diffs1) > max(diffs2)) {
			diffs <- diffs1
			run1 <- runA
			run2 <- runB
		} else {
			diffs <- diffs2
			run1 <- runB
			run2 <- runA
		}

		if (jitter != 0) {
			diffs <- diffs + rnorm(1, sd=jitter)
		}

		colorkey <- paste0(colorkey, plotcolors[i], ": ", run1, "-", run2, colorsep)

		lines(apply(diffs, 3, max), type=type, pch=pch, col=plotcolors[i])
		lines(apply(diffs, 3, min), type=type, pch=pch, col=plotcolors[i])
	}

	colorkey <- substr(colorkey, 1, nchar(colorkey)-nchar(colorsep)) # strip off the last, extra colorsep
	mtext(colorkey, 1) # 1 on bottom, 4 on the right side
}
