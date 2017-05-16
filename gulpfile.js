const gulp = require('gulp');
const ts = require('gulp-typescript');
const sourcemaps = require('gulp-sourcemaps');
const clean = require('gulp-clean');
const tsProject = ts.createProject('tsconfig.json');


gulp.task('default', () => {
    gulp.src('src/**/*.ts')
        .pipe(sourcemaps.init())
        .pipe(tsProject())
        .pipe(sourcemaps.write('.'))
        .pipe(gulp.dest('src'));
});

gulp.task('clean', () => {
    gulp.src('src/**/*.js', {read: false})
        .pipe(clean());
    
    gulp.src('src/**/*.js.map', {read: false})
        .pipe(clean());

    gulp.src('jims_memory.db', {read: false})
        .pipe(clean());
});

gulp.task('clean-build', ['clean', 'default'], () => {});
