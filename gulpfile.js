const gulp = require('gulp');
const ts = require('gulp-typescript');
const sourcemaps = require('gulp-sourcemaps');
const clean = require('gulp-clean');
const exec = require('child_process').exec;

const tsProject = ts.createProject('tsconfig.json');


gulp.task('default', ['build']);

gulp.task('clean', () => {
    gulp.src('src/**/*.js', {read: false})
        .pipe(clean());
    
    gulp.src('src/**/*.js.map', {read: false})
        .pipe(clean());

    gulp.src('*.db', {read: false})
        .pipe(clean());
});

gulp.task('clean-build', ['clean', 'default'], () => {});

gulp.task('build', () => {
    exec('tsc');
});

gulp.task('run', ['build'], () => {
    let proc = exec('node .\\src\\main.js');

    proc.stdout.pipe(process.stdout);
    proc.stderr.pipe(process.stderr);
});