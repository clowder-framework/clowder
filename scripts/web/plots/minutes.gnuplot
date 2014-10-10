set terminal png size 640,480
set output "tmp/minutes.png"
set ylabel "Tasks"
set xlabel "Time (Minutes)"
unset xtics
plot 'tmp/minutes.txt' using 3:4 with lines title "Tasks per Minute" lc rgb "blue"
