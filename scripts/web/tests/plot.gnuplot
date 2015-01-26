set terminal png size 640,480
set output "tmp/plot.png"
set ylabel "Elapsed Time (s)"
set xlabel "Date"
set xtics format " "
plot 'tmp/plot.txt' using 1:2 with lines title "" lc rgb "blue"
