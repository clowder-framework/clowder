set terminal png size 640,480
set output "tmp/days.png"
set ylabel "Tasks"
set xlabel "Time (Days)"
plot 'tmp/days.txt' using 3:4:xticlabels(2) with lines title "Tasks per Day" lc rgb "blue"
