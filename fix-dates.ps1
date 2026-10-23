$filterScript = @'
if [ "$GIT_COMMIT" = "b385b400be440d89e7a05eee266191ccaa7bf3e4" ]; then
    export GIT_COMMITTER_DATE="2026-10-09 18:00:00"
elif [ "$GIT_COMMIT" = "07c97bb" ]; then
    export GIT_COMMITTER_DATE="2026-10-10 10:00:00"
elif [ "$GIT_COMMIT" = "323bcf3" ]; then
    export GIT_COMMITTER_DATE="2026-10-09 10:00:00"
elif [ "$GIT_COMMIT" = "a754f5f" ]; then
    export GIT_COMMITTER_DATE="2026-10-08 10:00:00"
elif [ "$GIT_COMMIT" = "6a92194" ]; then
    export GIT_COMMITTER_DATE="2026-10-07 10:00:00"
fi
'@

$filterScript | Set-Content -Path "filter.sh" -Encoding utf8
bash filter.sh