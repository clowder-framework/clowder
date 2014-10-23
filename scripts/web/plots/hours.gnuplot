set terminal png size 640,480
set output "tmp/hours.png"
set ylabel "Tasks"
set xlabel "Time (Hours)"
plot 'tmp/hours.txt' using 3:4:xticlabels(2) with lines title "Tasks per Hour" lc rgb "blue"
